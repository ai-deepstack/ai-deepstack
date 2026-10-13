package org.deepstack.ai.runtime;

import org.deepstack.ai.engine.api.GraphRunRequest;
import org.deepstack.ai.engine.api.GraphRunResponse;
import org.deepstack.ai.engine.api.GraphSpec;
import org.deepstack.ai.engine.api.TraceConfig;
import org.deepstack.ai.engine.api.WorkflowState;
import org.deepstack.ai.engine.api.WorkflowStreamEvent;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.runtime.spi.GraphRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.UUID;

/**
 * 工作流运行时入口，{@link GraphRuntime} 实现：负责同步 / 流式执行与 HITL resume，
 * 并持有正在运行的执行注册表（取消 / 健康监控）。
 * <p>
 * 编译与缓存委托给 {@link WorkflowGraphCompiler}，卡片发射与 SSE 写入委托给 {@link WorkflowCardEmitter}，
 * 节点动作的构造与包装由 {@link NodeActionFactory} 承担；本类不再直接解析定义或包装节点。
 * </p>
 */
@Slf4j
@Component
public class WorkflowCompiler implements GraphRuntime {

    private final WorkflowGraphCompiler graphCompiler;
    private final WorkflowCardEmitter cardEmitter;
    private final Executor workflowExecutor;

    /**
     * 构造工作流编译器。
     *
     * @param graphCompiler    图编译与缓存
     * @param cardEmitter      卡片发射与 SSE 写入
     * @param workflowExecutor 工作流专用线程池
     */
    public WorkflowCompiler(WorkflowGraphCompiler graphCompiler,
                            WorkflowCardEmitter cardEmitter,
                            @Qualifier("workflowExecutor") Executor workflowExecutor) {
        this.graphCompiler = graphCompiler;
        this.cardEmitter = cardEmitter;
        this.workflowExecutor = workflowExecutor;
    }

    /**
     * 正在运行的工作流执行注册表，以 run ID 为键。
     * 用于取消：按 run ID 取消正在执行的工作流。
     */
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> runningExecutions = new ConcurrentHashMap<>();

    /**
     * 使用默认 TraceConfig（RECORD，不推流）执行工作流。
     */
    public GraphRunResponse execute(GraphSpec workflow, GraphRunRequest req) {
        log.info("Workflow execute (default TraceConfig): workflowId={}, code={}",
                workflow.getId(), workflow.getCode());
        return execute(workflow, req, new TraceConfig());
    }

    /**
     * 使用显式 TraceConfig 执行工作流。
     * 若 traceMode=NONE 时，响应中的 nodeExecutions 为空。
     * 启用 Checkpoint 且图因待确认卡片挂起时，status=WAITING_HUMAN。
     */
    public GraphRunResponse execute(GraphSpec workflow, GraphRunRequest req, TraceConfig traceConfig) {
        long startTime = System.currentTimeMillis();
        String threadId = resolveThreadId(req);
        log.info("Workflow execute: workflowId={}, code={}, userId={}, conversationId={}, threadId={}, resume={}, toolCodes={}, traceMode={}",
                workflow.getId(), workflow.getCode(),
                req.getUserId(), req.getConversationId(), threadId, req.isResume(), req.getToolCodes(),
                traceConfig != null ? traceConfig.getTraceMode() : null);

        GraphRunResponse response = new GraphRunResponse();
        response.setThreadId(threadId);

        try {
            CompiledGraph<WorkflowState> compiled = graphCompiler.compile(workflow, traceConfig);
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            RunContext run = RunContext.install(req.getUserId(), req.getConversationId(), req.getAgentCode(), threadId);
            run.setCardEmitter(cardEmitter.buildCardEmitter(threadId, null));

            Optional<NodeOutput<WorkflowState>> output;
            try {
                if (req.isResume()) {
                    Map<String, Object> updates = req.getResumeUpdates() != null
                            ? req.getResumeUpdates() : Map.of();
                    if (!updates.isEmpty()) {
                        config = compiled.updateState(config, updates);
                    }
                    output = compiled.invokeFinal(GraphInput.resume(), config);
                } else {
                    Map<String, Object> initialState = buildInitialState(req, traceConfig);
                    initialState.put(WorkflowState.THREAD_ID, threadId);
                    output = compiled.invokeFinal(GraphInput.args(initialState), config);
                }
            } finally {
                RunContext.clear();
            }

            fillResponseFromOutput(response, output, traceConfig, startTime);
            log.info("Workflow execute done: workflowId={}, status={}, threadId={}, durationMs={}",
                    workflow.getId(), response.getStatus(), threadId, response.getTotalDurationMs());
        } catch (Exception e) {
            log.error("Workflow execution failed: workflowCode={}, error={}", workflow.getCode(), e.getMessage(), e);
            response.applyStatus(org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.FAILED);
            response.setErrorMessage(e.getMessage());
            response.setTotalDurationMs((int) (System.currentTimeMillis() - startTime));
            response.setNodeExecutions(List.of());
            response.setTotalTokens(0);
        }

        return response;
    }

    /**
     * HITL：从 checkpoint 恢复执行。
     */
    public GraphRunResponse resume(GraphSpec workflow, String threadId, Map<String, Object> stateUpdates,
                                   GraphRunRequest baseRequest, TraceConfig traceConfig) {
        if (!StringUtils.hasText(threadId)) {
            throw new IllegalArgumentException("threadId 不能为空");
        }
        log.info("Workflow resume: workflowId={}, code={}, threadId={}, updateKeys={}",
                workflow != null ? workflow.getId() : null,
                workflow != null ? workflow.getCode() : null,
                threadId,
                stateUpdates != null ? stateUpdates.keySet() : Set.of());
        GraphRunRequest req = baseRequest != null ? baseRequest : new GraphRunRequest();
        req.setThreadId(threadId);
        req.setResume(true);
        req.setResumeUpdates(stateUpdates != null ? stateUpdates : Map.of());
        TraceConfig tc = traceConfig != null ? traceConfig : new TraceConfig();
        return execute(workflow, req, tc);
    }

    /**
     * 将图执行输出填充到 {@link GraphRunResponse}（状态、结果、节点执行明细）。
     *
     * @param response    待填充响应
     * @param output      LangGraph 末节点输出
     * @param traceConfig 追踪配置（决定是否带有 nodeExecutions）
     * @param startTime   开始时间毫秒
     */
    private void fillResponseFromOutput(GraphRunResponse response,
                                        Optional<NodeOutput<WorkflowState>> output,
                                        TraceConfig traceConfig,
                                        long startTime) {
        response.setTotalDurationMs((int) (System.currentTimeMillis() - startTime));
        response.setTotalTokens(0);
        if (output.isEmpty()) {
            response.applyStatus(org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.FAILED);
            response.setErrorMessage("图执行无输出");
            response.setNodeExecutions(List.of());
            return;
        }
        NodeOutput<WorkflowState> nodeOut = output.get();
        WorkflowState state = nodeOut.state();
        boolean ended = nodeOut.isEND();
        response.applyStatus(ended
                ? org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.SUCCESS
                : org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.WAITING_HUMAN);
        response.setResult(state != null
                ? state.value("response").map(Object::toString).orElse("")
                : "");
        if (!ended) {
            response.setNextNodeId(nodeOut.node());
        }
        if (traceConfig != null && traceConfig.shouldRecord() && state != null) {
            state.value(WorkflowState.NODE_EXECUTIONS).ifPresent(execs -> {
                if (execs instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> typed = (List<Map<String, Object>>) list;
                    response.setNodeExecutions(typed);
                }
            });
        }
        if (response.getNodeExecutions() == null) {
            response.setNodeExecutions(List.of());
        }
    }

    /**
     * 解析 Checkpoint threadId：请求显式的 &gt; conversationId &gt; 随机 run-xxx。
     */
    public String resolveThreadId(GraphRunRequest req) {
        if (req != null && StringUtils.hasText(req.getThreadId())) {
            return req.getThreadId().trim();
        }
        if (req != null && StringUtils.hasText(req.getConversationId())) {
            return "conv-" + req.getConversationId().trim();
        }
        return "run-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 以流式进度事件执行工作流。
     * 返回的 Flux 会发出：
     * <ul>
     *   <li>各节点开始执行时：NODE_START 事件</li>
     *   <li>各节点完成时：NODE_COMPLETE 事件</li>
     *   <li>最后 WORKFLOW_COMPLETE 事件（含整体结果）</li>
     * </ul>
     *
     * 要求 TraceConfig 满足 traceMode=STREAM 且 streamProgress=true。
     * 若未启用推流，则退化为单次 WORKFLOW_COMPLETE 事件。
     *
     * @param runId 可选 run ID，用于取消跟踪；为 null 时不可取消。
     */
    public Flux<WorkflowStreamEvent> executeStream(GraphSpec workflow, GraphRunRequest req, TraceConfig traceConfig, Long runId) {
        log.info("Workflow executeStream: workflowId={}, code={}, runId={}, userId={}, conversationId={}, stream={}",
                workflow.getId(), workflow.getCode(), runId,
                req.getUserId(), req.getConversationId(),
                traceConfig != null && traceConfig.shouldStream());
        if (!traceConfig.shouldStream()) {
            // 不具备流式能力：同步执行并仅发出一条完成事件
            log.info("Workflow executeStream degraded to sync: workflowId={}", workflow.getId());
            GraphRunResponse syncResult = execute(workflow, req, traceConfig);
            WorkflowStreamEvent complete = WorkflowStreamEvent.workflowComplete(
                    org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.ofRequired(syncResult.getStatus()),
                    syncResult.getResult(),
                    syncResult.getTotalDurationMs(),
                    syncResult.getNodeExecutions(),
                    syncResult.getErrorMessage()
            );
            return Flux.just(complete);
        }

        // 流式模式：使用感知流式的跟踪进行编译，在专用线程上执行
        String threadId = resolveThreadId(req);
        Sinks.Many<WorkflowStreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);

        // 使用流式包装器编译
        CompiledGraph<WorkflowState> compiled = graphCompiler.compileWithStream(workflow, traceConfig, sink);
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        // 在专用工作流线程池上异步执行（非 ForkJoinPool.commonPool）
        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            RunContext run = RunContext.install(req.getUserId(), req.getConversationId(), req.getAgentCode(), threadId);
            run.setStreamSink(sink);
            run.setCardEmitter(cardEmitter.buildCardEmitter(threadId, sink));
            try {
                Optional<NodeOutput<WorkflowState>> output;
                if (req.isResume()) {
                    Map<String, Object> updates = req.getResumeUpdates() != null
                            ? req.getResumeUpdates() : Map.of();
                    RunnableConfig cfg = config;
                    if (!updates.isEmpty()) {
                        cfg = compiled.updateState(cfg, updates);
                    }
                    output = compiled.invokeFinal(GraphInput.resume(), cfg);
                } else {
                    Map<String, Object> initialState = buildInitialState(req, traceConfig);
                    initialState.put(WorkflowState.THREAD_ID, threadId);
                    output = compiled.invokeFinal(GraphInput.args(initialState), config);
                }

                org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum status =
                        org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.SUCCESS;
                String finalResponse = "";
                List<Map<String, Object>> nodeExecutions = List.of();
                if (output.isPresent()) {
                    NodeOutput<WorkflowState> nodeOut = output.get();
                    if (!nodeOut.isEND()) {
                        status = org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.WAITING_HUMAN;
                    }
                    WorkflowState state = nodeOut.state();
                    finalResponse = state != null
                            ? state.value("response").map(Object::toString).orElse("")
                            : "";
                    if (traceConfig.shouldRecord() && state != null) {
                        Object execs = state.value(WorkflowState.NODE_EXECUTIONS).orElse(List.of());
                        if (execs instanceof List<?> list) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> typed = (List<Map<String, Object>>) list;
                            nodeExecutions = typed;
                        }
                    }
                }

                WorkflowCardEmitter.tryEmit(sink, WorkflowStreamEvent.workflowComplete(status, finalResponse, null, nodeExecutions));
            } catch (Exception e) {
                log.error("Workflow stream execution failed: workflowCode={}, error={}", workflow.getCode(), e.getMessage(), e);
                WorkflowCardEmitter.tryEmit(sink, WorkflowStreamEvent.workflowComplete(
                        org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.FAILED, null, null, List.of(), e.getMessage()));
            } finally {
                RunContext.clear();
                sink.tryEmitComplete();
                // 从运行注册表中移除
                if (runId != null) {
                    runningExecutions.remove(runId);
                }
            }
        }, workflowExecutor);

        // 注册以支持取消
        if (runId != null) {
            runningExecutions.put(runId, executionFuture);
        }

        return sink.asFlux();
    }

    /**
     * 注册可取消的后台执行（试跑异步启动时使用）。
     */
    public void registerRunningExecution(Long runId, CompletableFuture<Void> future) {
        if (runId == null || future == null) {
            return;
        }
        runningExecutions.put(runId, future);
    }

    /**
     * 按 run ID 取消正在运行的工作流执行。
     *
     * @param runId agent_workflow_run ID
     * @return 找到并取消则为 true；未找到或已完成则为 false
     */
    public boolean cancelExecution(Long runId) {
        if (runId == null) return false;
        CompletableFuture<Void> future = runningExecutions.remove(runId);
        if (future == null || future.isDone()) {
            return false;
        }
        boolean cancelled = future.cancel(true);
        log.info("Workflow execution cancelled: runId={}, cancelled={}", runId, cancelled);
        return cancelled;
    }

    /**
     * 获取编译缓存大小，用于健康监控。
     */
    public int getCompileCacheSize() {
        return graphCompiler.getCacheSize();
    }

    /**
     * 获取正在运行的执行数量，用于健康监控。
     */
    public int getRunningExecutionCount() {
        return runningExecutions.size();
    }

    /**
     * 使工作流相关缓存失效（定义更新时应调用）。
     */
    @Override
    public void invalidateCache(Long workflowId) {
        graphCompiler.invalidateCache(workflowId);
    }

    /**
     * 根据请求与 TraceConfig 构建初始状态 map。
     * 若请求提供 chatHistory，则注入聊天历史。
     */
    private Map<String, Object> buildInitialState(GraphRunRequest req, TraceConfig traceConfig) {
        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put("messages", new ArrayList<>(List.of(req.getMessage())));
        initialState.put("response", "");
        initialState.put("intent", "");
        initialState.put("context_vars", new HashMap<String, Object>());
        initialState.put(WorkflowState.NODE_EXECUTIONS, new ArrayList<>());
        // 将执行配置注入 state
        initialState.put(WorkflowState.EXECUTION_CONFIG, traceConfig.toStateConfig());
        // 若有聊天历史则注入
        if (req.getChatHistory() != null && !req.getChatHistory().isEmpty()) {
            initialState.put(WorkflowState.CHAT_HISTORY, new ArrayList<>(req.getChatHistory()));
        }
        if (req.getUserId() != null) {
            initialState.put(WorkflowState.USER_ID, req.getUserId());
        }
        if (req.getConversationId() != null) {
            initialState.put(WorkflowState.CONVERSATION_ID, req.getConversationId());
        }
        if (req.getAgentCode() != null) {
            initialState.put(WorkflowState.AGENT_CODE, req.getAgentCode());
        }
        if (req.getToolCodes() != null && !req.getToolCodes().isEmpty()) {
            initialState.put(WorkflowState.TOOL_CODES, new ArrayList<>(req.getToolCodes()));
        }
        return initialState;
    }
}
