package org.deepstack.ai.runtime;


import org.deepstack.ai.engine.TraceConfig;
import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.kernel.enums.agent.TraceModeEnum;
import org.deepstack.ai.engine.WorkflowStreamEvent;
import org.deepstack.ai.engine.node.AssignNode;
import org.deepstack.ai.engine.node.CardGateNode;
import org.deepstack.ai.engine.node.ConditionNode;
import org.deepstack.ai.engine.node.HitlAwareNodeAction;
import org.deepstack.ai.engine.node.HitlInterrupts;
import org.deepstack.ai.engine.node.IntentClassifierNode;
import org.deepstack.ai.engine.node.LLMReasonNode;
import org.deepstack.ai.engine.node.MemoryNode;
import org.deepstack.ai.engine.node.RAGRetrieveNode;
import org.deepstack.ai.engine.node.ToolCallNode;
import org.deepstack.ai.engine.support.ConditionEvaluator;
import org.deepstack.ai.engine.support.NodeTimeoutConfig;
import org.deepstack.ai.engine.validate.NodePropertiesValidator;
import org.deepstack.ai.engine.validate.WorkflowDefinitionValidator;
import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.engine.GraphSpec;
import org.deepstack.ai.runtime.spi.EmittedCard;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.observability.AgentRunMetricNames;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 工作流编译器，{@link GraphRuntime} 实现：JSON 定义 → LangGraph4j CompiledGraph，并负责执行 / 流式 / HITL resume。
 */
@Slf4j
@Component
public class WorkflowCompiler implements GraphRuntime {

    private final AgentNodeContext agentNodeContext;
    private final Executor workflowExecutor;
    private final ObjectProvider<BaseCheckpointSaver> checkpointSaver;
    private final SysConfigPort sysConfigPort;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    /**
     * 构造工作流编译器。
     *
     * @param agentNodeContext  节点运行时上下文
     * @param workflowExecutor  工作流专用线程池
     * @param checkpointSaver   Checkpoint 保存器（可选）
     * @param sysConfigPort     系统配置端口
     * @param meterRegistry     Micrometer 注册表（可选）
     */
    public WorkflowCompiler(AgentNodeContext agentNodeContext,
                            @Qualifier("workflowExecutor") Executor workflowExecutor,
                            ObjectProvider<BaseCheckpointSaver> checkpointSaver,
                            SysConfigPort sysConfigPort,
                            ObjectProvider<MeterRegistry> meterRegistry) {
        this.agentNodeContext = agentNodeContext;
        this.workflowExecutor = workflowExecutor;
        this.checkpointSaver = checkpointSaver;
        this.sysConfigPort = sysConfigPort;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 工作流级默认超时（毫秒），取�?definition.executionConfig.defaultTimeoutMs�?
     * �?null 时使�?NodeTimeoutConfig.DEFAULT_TIMEOUT_SECONDS�?0 秒）�?     * <p>
     * 现为 compile 方法内的局部变量，而非实例字段�?
     * 以避免本单例上并�?compile 时产生竞态�?     * </p>
     */

    /**
     * 正在运行的工作流执行注册表，�?run ID 为键�?
     * 用于取消：按 run ID 取消正在执行的工作流�?     */
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> runningExecutions = new ConcurrentHashMap<>();

    /**
     * 编译缓存：cacheKey（workflowId + version + definition 哈希 + traceMode）→ CompiledGraph�?
     * 避免对同一工作流每次请求都重新解析 JSON 并重建图�?     */
    private final ConcurrentHashMap<String, CompiledGraph<WorkflowState>> compileCache = new ConcurrentHashMap<>();

    /**
     * 编译缓存最大条目数。超出时移除较旧的条目�?
     */
    private static final int COMPILE_CACHE_MAX_SIZE = 200;

    // ===== 编译 =====

    /**
     * 将工作流 JSON 定义编译�?LangGraph4j CompiledGraph�?
     * 使用默认 TraceConfig（RECORD 模式，不推流）�?     * 结果�?workflowId + version + definition 哈希 + traceMode 缓存�?     */
    public CompiledGraph<WorkflowState> compile(GraphSpec workflow) {
        log.info("Workflow compile (default TraceConfig): workflowId={}, code={}, version={}",
                workflow.getId(), workflow.getCode(), workflow.getVersion());
        return compile(workflow, new TraceConfig());
    }

    /**
     * 按显�?TraceConfig 编译工作�?JSON 定义�?
     * TraceConfig 控制节点是否记录执行轨迹以及/或推送流式事件�?     * 结果�?workflowId + version + definition 哈希 + traceMode 缓存�?     */
    @SuppressWarnings("unchecked")
    public CompiledGraph<WorkflowState> compile(GraphSpec workflow, TraceConfig traceConfig) {
        log.info("Workflow compile: workflowId={}, code={}, version={}, traceMode={}",
                workflow.getId(), workflow.getCode(), workflow.getVersion(),
                traceConfig != null ? traceConfig.getTraceMode() : null);
        String cacheKey = buildCacheKey(workflow, traceConfig);
        CompiledGraph<WorkflowState> cached = compileCache.get(cacheKey);
        if (cached != null) {
            log.debug("Workflow compile cache hit: key={}", cacheKey);
            return cached;
        }

        log.debug("Workflow compile cache miss: key={}", cacheKey);
        CompiledGraph<WorkflowState> compiled = doCompile(workflow, traceConfig);
        log.info("Workflow compile done: workflowId={}, code={}, cacheKey={}",
                workflow.getId(), workflow.getCode(), cacheKey);
        // 写入编译缓存（容量守卫：满容时随机移除一条）
        if (compileCache.size() >= COMPILE_CACHE_MAX_SIZE) {
            // 只删一条而非清空全部（避免编译风暴）
            var iter = compileCache.entrySet().iterator();
            if (iter.hasNext()) {
                iter.next();
                iter.remove();
            }
            log.debug("Workflow compile cache evicted one entry (at {} capacity)", COMPILE_CACHE_MAX_SIZE);
        }
        compileCache.putIfAbsent(cacheKey, compiled);
        return compiled;
    }

    /**
     * 使工作流相关缓存失效（定义更新时应调用）�?
     */
    public void invalidateCache(Long workflowId) {
        if (workflowId == null) return;
        String prefix = "wf:" + workflowId + ":";
        int removed = 0;
        var it = compileCache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey().startsWith(prefix)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Workflow compile cache invalidated: workflowId={}, entries={}", workflowId, removed);
        }
    }

    /**
     * 构建缓存键：wf:{workflowId}:{version}:{definitionHash}:{traceMode}
     */
    private String buildCacheKey(GraphSpec workflow, TraceConfig traceConfig) {
        String defHash = sha256Short(workflow.getDefinition());
        String traceMode = traceConfig != null ? traceConfig.getTraceMode() : TraceModeEnum.RECORD.name();
        return "wf:" + workflow.getId() + ":" + workflow.getVersion() + ":" + defHash + ":" + traceMode;
    }

    /**
     * 计算 definition 字符串的�?SHA-256 哈希（前 16 位十六进制）�?
     */
    private String sha256Short(String input) {
        if (input == null || input.isEmpty()) return "empty";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // 回退：使�?hashCode
        return "h" + Integer.toHexString(input.hashCode());
        }
    }

    /**
     * 实际编译逻辑（不做缓存）。无 sink，不推送流式节点事件�?
     */
    private CompiledGraph<WorkflowState> doCompile(GraphSpec workflow, TraceConfig traceConfig) {
        return doCompile(workflow, traceConfig, null);
    }

    /**
     * 实际编译逻辑（不做缓存）�?
     * {@code sink} 非空�?{@link TraceConfig#shouldStream()} 时，节点包装器会推�?NODE_START / NODE_COMPLETE�?     */
    @SuppressWarnings("unchecked")
    private CompiledGraph<WorkflowState> doCompile(GraphSpec workflow, TraceConfig traceConfig,
                                                   Sinks.Many<WorkflowStreamEvent> sink) {
        Map<String, Object> definition = parseDefinition(workflow.getDefinition());
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition is null: " + workflow.getCode());
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.getOrDefault("nodes", List.of());
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.getOrDefault("edges", List.of());
        Map<String, Object> variables = (Map<String, Object>) definition.getOrDefault("variables", Map.of());

        // 校验图结构（孤立节点、环、缺少起止节点、悬空边�?
        WorkflowDefinitionValidator.validate(workflow.getCode(), nodes, edges, workflow.getGraphType());

        // �?executionConfig 提取工作流级默认超时（局部变量，非实例字段）
        Map<String, Object> executionConfig = (Map<String, Object>) definition.getOrDefault("executionConfig", Map.of());
        Integer workflowDefaultTimeoutMs = extractWorkflowDefaultTimeout(executionConfig);

        // 构建 State schema
        Map<String, Channel<?>> schema = WorkflowState.defaultSchema();
        variables.keySet().forEach(k -> schema.put(k, Channels.base(() -> "")));

        try {
            StateGraph<WorkflowState> graph = new StateGraph<>(schema, WorkflowState::new);

            // 1. 注册全部节点（按 TraceConfig 包装执行跟踪�?
            for (Map<String, Object> nodeDef : nodes) {
                String nodeId = (String) nodeDef.get("id");
                String type = (String) nodeDef.get("type");
                String name = (String) nodeDef.getOrDefault("name", nodeId);
                Map<String, Object> properties = (Map<String, Object>) nodeDef.getOrDefault("properties", Map.of());

                // 编译期校验必填属性（快速失败）
                NodePropertiesValidator.validate(nodeId, type, properties);

                AsyncNodeAction<WorkflowState> action = createNodeAction(type, properties, nodeId, name);

                // 解析节点级超�?
                NodeTimeoutConfig timeoutConfig = NodeTimeoutConfig.resolve(properties, workflowDefaultTimeoutMs);
                AsyncNodeAction<WorkflowState> timedAction = wrapWithTimeout(nodeId, action, timeoutConfig);

                boolean autoHitl = checkpointSaver.getIfAvailable() != null
                        && sysConfigPort.isYes(SysConfigKeys.CHECKPOINT_ENABLED);
                AsyncNodeAction<WorkflowState> tracked = wrapWithTracking(
                        nodeId, name, type, timedAction, action, autoHitl, traceConfig, sink);
                graph.addNode(nodeId, tracked);
            }

            // 2. 收集条件�?
            Map<String, List<Map<String, Object>>> conditionalEdges = new LinkedHashMap<>();
            List<Map<String, Object>> normalEdges = new ArrayList<>();

            for (Map<String, Object> edgeDef : edges) {
                String source = (String) edgeDef.get("sourceNodeId");
                Map<String, Object> props = (Map<String, Object>) edgeDef.getOrDefault("properties", Map.of());
                String condition = props != null ? (String) props.get("condition") : null;

                if (condition != null && !condition.isEmpty()) {
                    conditionalEdges.computeIfAbsent(source, k -> new ArrayList<>()).add(edgeDef);
                } else {
                    normalEdges.add(edgeDef);
                }
            }

            // 3. 注册普通边
            for (Map<String, Object> edgeDef : normalEdges) {
                String source = (String) edgeDef.get("sourceNodeId");
                String target = (String) edgeDef.get("targetNodeId");
                // 若该 source 同时有条件边，则跳过普通边�?                // LangGraph4j 不允许同一 source 节点同时存在普通边与条件边
                if (conditionalEdges.containsKey(source)) {
                    log.debug("Skipping normal edge {} -> {} because source has conditional edges", source, target);
                    continue;
                }
                graph.addEdge(source, target);
            }

            // 4. 注册条件边（增强：支持任意状态变量表达式�?
            for (var entry : conditionalEdges.entrySet()) {
                String sourceId = entry.getKey();
                List<Map<String, Object>> condList = entry.getValue();
                Map<String, String> mapping = new LinkedHashMap<>();
                for (Map<String, Object> e : condList) {
                    String cond = (String) ((Map<String, Object>) e.getOrDefault("properties", Map.of())).get("condition");
                    String target = (String) e.get("targetNodeId");
                    mapping.put(cond, target);
                }
                AsyncEdgeAction<WorkflowState> edgeCondition = state -> {
                    // �?ConditionEvaluator 依次尝试各条件，支持丰富表达�?
                    for (var condEntry : mapping.entrySet()) {
                        String condExpr = condEntry.getKey();
                        if (ConditionEvaluator.evaluate(condExpr, state)) {
                            return CompletableFuture.completedFuture(condEntry.getKey());
                        }
                    }
                    // 无一匹配 —�?若有 DEFAULT 则用�?
                    if (mapping.containsKey("DEFAULT")) {
                        return CompletableFuture.completedFuture("DEFAULT");
                    }
                    // �?DEFAULT：告警并选第一条路由（容错，非静默失败�?
                    String firstKey = mapping.keySet().iterator().next();
                    log.warn("Conditional edge on node '{}': no condition matched and no DEFAULT. "
                            + "Falling back to first route '{}'. Conditions: {}", sourceId, firstKey, mapping.keySet());
                    return CompletableFuture.completedFuture(firstKey);
                };
                graph.addConditionalEdges(sourceId, edgeCondition, mapping);
            }

            // 5. 设置入口边（START -> start-node�?
            Optional<Map<String, Object>> startNodeOpt = nodes.stream()
                    .filter(n -> "start-node".equals(n.get("type")))
                    .findFirst();
            if (startNodeOpt.isPresent()) {
                graph.addEdge(START, (String) startNodeOpt.get().get("id"));
            }

            // 6. 设置出口边（end-node -> END�?
            for (Map<String, Object> endNode : nodes.stream().filter(n -> "end-node".equals(n.get("type"))).toList()) {
                graph.addEdge((String) endNode.get("id"), END);
            }

            CompileConfig.Builder cfg = CompileConfig.builder();
            BaseCheckpointSaver saver = checkpointSaver.getIfAvailable();
            if (saver != null && sysConfigPort.isYes(SysConfigKeys.CHECKPOINT_ENABLED)) {
                cfg.checkpointSaver(saver);
                // HITL：保留线程检查点，供 confirm/edit/reject resume�?                // 挂起�?InterruptableAction（显�?card-gate 或下一节点自动门闩）判定，
                // 不用 interruptAfter：无卡时也会无条件停下�?
                cfg.releaseThread(false);
            }
            return graph.compile(cfg.build());
        } catch (GraphStateException e) {
            throw new IllegalArgumentException("Failed to compile workflow: " + e.getMessage(), e);
        }
    }

    // ===== 跟踪包装�?=====

    /**
     * �?TraceConfig 为节点动作包装执行跟踪�?
     * <ul>
     *   <li>NONE �?透传，不记录也不推流�?/li>
     *   <li>RECORD �?�?nodeId、name、type、input、output、durationMs、status 写入 NODE_EXECUTIONS�?/li>
     *   <li>STREAM �?RECORD + 通过 sink 推送中间进度事件（sink 非空�?shouldStream 时）�?/li>
     * </ul>
     *
     * @param sink 可为 null；为 null �?{@code !traceConfig.shouldStream()} 时不推�?NODE_START / NODE_COMPLETE
     */
    private AsyncNodeAction<WorkflowState> wrapWithTracking(String nodeId, String name, String type,
                                                            AsyncNodeAction<WorkflowState> delegate,
                                                            AsyncNodeAction<WorkflowState> interruptSource,
                                                            boolean autoHitl,
                                                            TraceConfig traceConfig,
                                                            Sinks.Many<WorkflowStreamEvent> sink) {
        AsyncNodeAction<WorkflowState> trackedApply;
        // NONE 模式：仍收集 pending_cards（HITL 需要），并上报节点耗时指标
        if (traceConfig.isTraceNone()) {
            trackedApply = state -> {
                long startNs = System.nanoTime();
                return delegate.apply(state).handle((output, error) -> {
                    long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                    if (error != null) {
                        RunContext.drainPendingCards();
                        recordNodeDuration(type, GraphRunStatusEnum.FAILED.name(), durationMs);
                        return error;
                    }
                    recordNodeDuration(type, GraphRunStatusEnum.SUCCESS.name(), durationMs);
                    Map<String, Object> update = new LinkedHashMap<>(output != null ? output : Map.of());
                    mergePendingCardsIntoUpdate(state, update);
                    clearResumeCardAction(state, type, update);
                    return update;
                }).thenCompose(result -> {
                    if (result instanceof Throwable t) {
                        Throwable cause = t instanceof java.util.concurrent.CompletionException
                                && t.getCause() != null ? t.getCause() : t;
                        return CompletableFuture.failedFuture(cause);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> update = (Map<String, Object>) result;
                    return CompletableFuture.completedFuture(update);
                });
            };
        } else {
            boolean emitStream = sink != null && traceConfig.shouldStream();
            trackedApply = state -> {
                long startNs = System.nanoTime();
                String inputSnapshot = traceConfig.shouldRecord() ? summarizeState(state) : "";

                if (emitStream) {
                    tryEmit(sink, WorkflowStreamEvent.nodeStart(nodeId, name, type, inputSnapshot));
                }

                // 不用 join：避免在 workflowExecutor 线程上阻塞等待，嵌套提交时可能死锁
                return delegate.apply(state).handle((output, error) -> {
                    long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                    if (error != null) {
                        RunContext.drainPendingCards();
                        recordNodeDuration(type, GraphRunStatusEnum.FAILED.name(), durationMs);
                        if (traceConfig.shouldRecord()) {
                            Map<String, Object> failRec = org.deepstack.ai.runtime.observability.NodeExecutionRecords
                                    .failed(nodeId, name, type, inputSnapshot, durationMs,
                                            error.getMessage());
                            Map<String, Object> failUpdate = new LinkedHashMap<>();
                            failUpdate.put(WorkflowState.NODE_EXECUTIONS, List.of(failRec));
                            if (emitStream) {
                                tryEmit(sink, WorkflowStreamEvent.nodeComplete(
                                        nodeId, name, type, durationMs,
                                        GraphRunStatusEnum.FAILED,
                                        error.getMessage()));
                            }
                            // 失败仍通过 thenCompose 抛出；记录靠 appender 无法在失败路径写入时，
                            // 至少推送 SSE；同步路径依赖异常上抛由编排层记 error_code
                            log.warn("wrapWithTracking 节点失败: nodeId={}, type={}, durationMs={}, err={}",
                                    nodeId, type, durationMs, error.getMessage());
                        } else if (emitStream) {
                            tryEmit(sink, WorkflowStreamEvent.nodeComplete(
                                    nodeId, name, type, durationMs,
                                    GraphRunStatusEnum.FAILED,
                                    error.getMessage()));
                        }
                        return error;
                    }
                    Map<String, Object> update = new LinkedHashMap<>(output != null ? output : Map.of());
                    mergePendingCardsIntoUpdate(state, update);
                    clearResumeCardAction(state, type, update);

                    recordNodeDuration(type, GraphRunStatusEnum.SUCCESS.name(), durationMs);
                    if (traceConfig.shouldRecord()) {
                        Map<String, Object> record = org.deepstack.ai.runtime.observability.NodeExecutionRecords
                                .success(nodeId, name, type, inputSnapshot,
                                        summarizeOutput(output), durationMs);
                        update.put(WorkflowState.NODE_EXECUTIONS, List.of(record));
                        log.debug("wrapWithTracking 节点成功: nodeId={}, type={}, durationMs={}",
                                nodeId, type, durationMs);

                        if (emitStream) {
                            tryEmit(sink, WorkflowStreamEvent.nodeComplete(
                                    nodeId, name, type, durationMs,
                                    GraphRunStatusEnum.SUCCESS, null));
                        }
                    }
                    return update;
                }).thenCompose(result -> {
                    if (result instanceof Throwable t) {
                        Throwable cause = t instanceof java.util.concurrent.CompletionException
                                && t.getCause() != null ? t.getCause() : t;
                        return CompletableFuture.failedFuture(cause);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> update = (Map<String, Object>) result;
                    return CompletableFuture.completedFuture(update);
                });
            };
        }
        return new HitlAwareNodeAction(type, trackedApply, interruptSource, autoHitl);
    }

    /**
     * 上报节点耗时 Timer（{@link AgentRunMetricNames#NODE_DURATION}）。
     * <p>
     * 标签：agent / type / status。无 MeterRegistry 时静默跳过。
     * </p>
     *
     * @param nodeType   节点类型
     * @param status     SUCCESS / FAILED
     * @param durationMs 耗时毫秒
     */
    private void recordNodeDuration(String nodeType, String status, long durationMs) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;
        }
        RunContext ctx = RunContext.current();
        String agent = (ctx != null && StringUtils.hasText(ctx.agentCode()))
                ? ctx.agentCode().trim()
                : AgentRunMetricNames.UNKNOWN;
        String type = StringUtils.hasText(nodeType) ? nodeType.trim() : AgentRunMetricNames.UNKNOWN;
        String st = StringUtils.hasText(status) ? status.trim() : AgentRunMetricNames.UNKNOWN;
        Timer.builder(AgentRunMetricNames.NODE_DURATION)
                .tag(AgentRunMetricNames.TAG_AGENT, agent)
                .tag(AgentRunMetricNames.TAG_TYPE, type)
                .tag(AgentRunMetricNames.TAG_STATUS, st)
                .register(registry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
        log.debug("recordNodeDuration: agent={}, type={}, status={}, durationMs={}",
                agent, type, st, durationMs);
    }

    /**
     * 自动门闩 resume 后清�?card_action，避免后续节点误判「已确认」而不再挂起�?
     * 显式 card-gate �?apply 已负责清理�?     */
    private void clearResumeCardAction(WorkflowState state, String type, Map<String, Object> update) {
        if ("card-gate-node".equals(type) || !HitlInterrupts.hasCardAction(state)) {
            return;
        }
        update.put(WorkflowState.CARD_ACTION, "");
    }

    /** 向流�?sink 推送事件；失败仅打 warn，不中断执行�?*/
    private void tryEmit(Sinks.Many<WorkflowStreamEvent> sink, WorkflowStreamEvent event) {
        if (sink == null) {
            return;
        }
        try {
            sink.tryEmitNext(event);
        } catch (Exception e) {
            log.warn("Failed to emit stream event: {}", e.getMessage());
        }
    }

    /**
     * 将本节点缓冲的出卡写�?{@code pending_cards}（追加）�?
     */
    @SuppressWarnings("unchecked")
    private void mergePendingCardsIntoUpdate(WorkflowState state, Map<String, Object> update) {
        List<Map<String, Object>> emitted = RunContext.drainPendingCards();
        if (emitted.isEmpty()) {
            return;
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        Object existing = update.get(WorkflowState.PENDING_CARDS);
        if (existing instanceof List<?> fromUpdate) {
            for (Object o : fromUpdate) {
                if (o instanceof Map<?, ?> m) {
                    merged.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        } else {
            state.value(WorkflowState.PENDING_CARDS).ifPresent(v -> {
                if (v instanceof List<?> fromState) {
                    for (Object o : fromState) {
                        if (o instanceof Map<?, ?> m) {
                            merged.add(new LinkedHashMap<>((Map<String, Object>) m));
                        }
                    }
                }
            });
        }
        merged.addAll(emitted);
        update.put(WorkflowState.PENDING_CARDS, merged);
        log.info("HITL pending_cards += {} (total={})", emitted.size(), merged.size());
    }

    /**
     * 图执行期间的卡片发射器：�?threadId、记 pending、回�?CardPort�?
     */
    private Consumer<Object> buildCardEmitter(String threadId, Sinks.Many<WorkflowStreamEvent> sink) {
        return card -> {
            try {
                EmittedCard emitted = EmittedCard.from(card);
                if (emitted != null && !StringUtils.hasText(emitted.getThreadId())) {
                    emitted.setThreadId(threadId);
                }
                Map<String, Object> meta = new LinkedHashMap<>();
                if (emitted != null) {
                    if (StringUtils.hasText(emitted.getCardId())) {
                        meta.put("cardId", emitted.getCardId());
                    }
                    if (StringUtils.hasText(emitted.getCardType())) {
                        meta.put("cardType", emitted.getCardType());
                    }
                    if (StringUtils.hasText(emitted.getThreadId())) {
                        meta.put("threadId", emitted.getThreadId());
                    }
                }
                if (!meta.isEmpty()) {
                    RunContext.recordPendingCard(meta);
                }
                if (sink != null) {
                    String cardJson = JSON.toJSONString(emitted != null ? emitted : card);
                    tryEmit(sink, WorkflowStreamEvent.card(null, null, cardJson));
                }
                agentNodeContext.getCardPort().afterEmitted(emitted);
            } catch (Exception e) {
                log.warn("Failed to emit card event: {}", e.getMessage());
            }
        };
    }

    // ===== 超时包装�?=====

    /**
     * 为节点动作包装超时守卫�?
     * 若委托未在配置超时内完成，CompletableFuture 将以 TimeoutException 异常完成�?     * 跟踪包装器会捕获并记�?FAILED 执行�?     *
     * @param nodeId        节点标识（用于日志）
     * @param delegate      原始节点动作
     * @param timeoutConfig 节点级超时配�?
     * @return 带超时守卫的节点动作
     */
    private AsyncNodeAction<WorkflowState> wrapWithTimeout(String nodeId,
                                                            AsyncNodeAction<WorkflowState> delegate,
                                                            NodeTimeoutConfig timeoutConfig) {
        return state -> {
            CompletableFuture<Map<String, Object>> future = delegate.apply(state);
            return future.orTimeout(timeoutConfig.getTimeoutMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        if (ex instanceof TimeoutException
                                || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)) {
                            log.error("Node '{}' timed out after {}ms", nodeId, timeoutConfig.getTimeoutMillis());
                            throw new RuntimeException("Node '" + nodeId + "' timed out after "
                                    + timeoutConfig.getTimeoutMillis() + "ms", ex);
                        }
                        // 其他异常：按 RuntimeException 包装后重新抛�?
                        throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
                    });
        };
    }

    /**
     * �?executionConfig 提取工作流级默认超时�?
     *
     * @param executionConfig 工作流定义中�?executionConfig �?
     * @return 默认超时（毫秒），未配置则为 null
     */
    private Integer extractWorkflowDefaultTimeout(Map<String, Object> executionConfig) {
        if (executionConfig == null) return null;
        Object val = executionConfig.get("defaultTimeoutMs");
        if (val instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return null;
    }

    // ===== 状态摘�?=====

    /**
     * 生成当前状态的轻量快照，用于节点输入日志�?
     */
    private String summarizeState(WorkflowState state) {
        try {
            String intent = state.value(WorkflowState.INTENT).map(Object::toString).orElse("");
            String response = state.value(WorkflowState.RESPONSE).map(Object::toString).orElse("");
            Object messages = state.value(WorkflowState.MESSAGES).orElse(List.of());
            String msgSummary = messages instanceof List<?> list ? "messages[" + list.size() + "]" : "messages[0]";
            return msgSummary + " intent=" + intent + " response=" + (response.length() > 200 ? response.substring(0, 200) + "..." : response);
        } catch (Exception e) {
            return "<state-summary-error>";
        }
    }

    /**
     * 摘要节点输出 map，用于日志�?
     */
    private String summarizeOutput(Map<String, Object> output) {
        if (output == null || output.isEmpty()) return "";
        try {
            String json = JSON.toJSONString(output);
            return json.length() > 500 ? json.substring(0, 500) + "..." : json;
        } catch (Exception e) {
            return output.toString();
        }
    }

    // ===== 节点工厂 =====

    /**
     * 按节点类型创�?NodeAction，对需要服务的节点注入 AgentNodeContext�?
     */
    @SuppressWarnings("unchecked")
    private AsyncNodeAction<WorkflowState> createNodeAction(String type, Map<String, Object> properties) {
        return createNodeAction(type, properties, null, null);
    }

    /**
     * 按节点类型创�?NodeAction，对需要服务的节点注入 AgentNodeContext�?
     *
     * @param type       节点类型编码
     * @param properties 节点属�?
     * @param nodeId     节点 ID（llm-node 等需要）
     * @param nodeName   节点显示�?
     */
    @SuppressWarnings("unchecked")
    private AsyncNodeAction<WorkflowState> createNodeAction(String type, Map<String, Object> properties,
                                                             String nodeId, String nodeName) {
        switch (type) {
            case "start-node":
                return (AsyncNodeAction<WorkflowState>) (AsyncNodeAction<?>) node_async(state -> Map.of());
            case "end-node":
                return (AsyncNodeAction<WorkflowState>) (AsyncNodeAction<?>) node_async(state -> Map.of());
            case "intent-node":
                return new IntentClassifierNode(agentNodeContext, properties);
            case "llm-node":
                return new LLMReasonNode(agentNodeContext, properties, nodeId, nodeName);
            case "rag-node":
                return new RAGRetrieveNode(agentNodeContext, properties);
            case "tool-node":
                return new ToolCallNode(agentNodeContext, properties);
            case "condition-node":
                return new ConditionNode(properties);
            case "memory-node":
                return new MemoryNode(agentNodeContext, properties);
            case "card-gate-node":
                return new CardGateNode(properties);
            case "assign-node":
                return new AssignNode(properties);
            default:
                throw new IllegalArgumentException(
                        "Unknown node type '" + type + "'. Supported types: start-node, end-node, intent-node, "
                                + "llm-node, rag-node, tool-node, condition-node, memory-node, card-gate-node, assign-node");
        }
    }

    // ===== 构建初始状�?=====

    /**
     * 根据请求�?TraceConfig 构建初始状�?map�?
     * 若请求提�?chatHistory，则注入聊天历史�?     */
    private Map<String, Object> buildInitialState(GraphRunRequest req, TraceConfig traceConfig) {
        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put("messages", new ArrayList<>(List.of(req.getMessage())));
        initialState.put("response", "");
        initialState.put("intent", "");
        initialState.put("context_vars", new HashMap<String, Object>());
        initialState.put(WorkflowState.NODE_EXECUTIONS, new ArrayList<>());
        // 将执行配置注�?state
        initialState.put(WorkflowState.EXECUTION_CONFIG, traceConfig.toStateConfig());
        // 若有聊天历史则注�?
        if (req.getChatHistory() != null && !req.getChatHistory().isEmpty()) {
            initialState.put(WorkflowState.CHAT_HISTORY, new ArrayList<>(req.getChatHistory()));
        }
        if (req.getUserId() != null) {
            initialState.put(WorkflowState.USER_ID, req.getUserId());
        }
        if (req.getConversationId() != null) {
            initialState.put(WorkflowState.CONVERSATION_ID, req.getConversationId());
        }
        if (req.getToolCodes() != null && !req.getToolCodes().isEmpty()) {
            initialState.put(WorkflowState.TOOL_CODES, new ArrayList<>(req.getToolCodes()));
        }
        return initialState;
    }

    /**
     * 从工作流定义 JSON 提取 executionConfig�?
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractExecutionConfig(GraphSpec workflow) {
        Map<String, Object> definition = parseDefinition(workflow.getDefinition());
        if (definition == null) return null;
        Object cfg = definition.get("executionConfig");
        if (cfg instanceof Map) return (Map<String, Object>) cfg;
        return null;
    }

    // ===== 执行（同步） =====

    /**
     * 使用默认 TraceConfig（RECORD，不推流）执行工作流�?
     */
    public GraphRunResponse execute(GraphSpec workflow, GraphRunRequest req) {
        log.info("Workflow execute (default TraceConfig): workflowId={}, code={}",
                workflow.getId(), workflow.getCode());
        return execute(workflow, req, new TraceConfig());
    }

    /**
     * 使用显式 TraceConfig 执行工作流�?
     * �?traceMode=NONE 时，响应中的 nodeExecutions 为空�?     * 启用 Checkpoint 且图因待确认卡片挂起时，status=WAITING_HUMAN�?     */
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
            CompiledGraph<WorkflowState> compiled = compile(workflow, traceConfig);
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            RunContext run = RunContext.install(req.getUserId(), req.getConversationId(), req.getAgentCode(), threadId);
            run.setCardEmitter(buildCardEmitter(threadId, null));

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
     * HITL：从 checkpoint 恢复执行�?
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
     * 将图执行输出填充�?{@link GraphRunResponse}（状态、结果、节点执行明细）�?
     *
     * @param response    待填充响�?
     * @param output      LangGraph 末节点输�?     * @param traceConfig 追踪配置（决定是否带�?nodeExecutions�?     * @param startTime   开始时间毫�?     */
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
     * 解析 Checkpoint threadId：请求显式�?&gt; conversationId &gt; 随机 run-xxx�?
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

    // ===== 执行（流式） =====

    /**
     * 以流式进度事件执行工作流�?
     * 返回�?Flux 会发出：
     * <ul>
     *   <li>各节点开始执行时�?NODE_START 事件</li>
     *   <li>各节点完成时�?NODE_COMPLETE 事件</li>
     *   <li>最�?WORKFLOW_COMPLETE 事件（含整体结果�?/li>
     * </ul>
     *
     * 要求 TraceConfig �?traceMode=STREAM �?streamProgress=true�?
     * 若未启用推流，则退化为单次 WORKFLOW_COMPLETE 事件�?     *
     * @param runId 可�?run ID，用于取消跟踪；�?null 时不可取�?
     */
    public Flux<WorkflowStreamEvent> executeStream(GraphSpec workflow, GraphRunRequest req, TraceConfig traceConfig, Long runId) {
        log.info("Workflow executeStream: workflowId={}, code={}, runId={}, userId={}, conversationId={}, stream={}",
                workflow.getId(), workflow.getCode(), runId,
                req.getUserId(), req.getConversationId(),
                traceConfig != null && traceConfig.shouldStream());
        if (!traceConfig.shouldStream()) {
            // 不具备流式能力：同步执行并仅发出一条完成事�?
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

        // 流式模式：使用感知流式的跟踪进行编译，在专用线程上执�?
        String threadId = resolveThreadId(req);
        Sinks.Many<WorkflowStreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);

        // 使用流式包装器编�?
        CompiledGraph<WorkflowState> compiled = compileWithStream(workflow, traceConfig, sink);
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        // 在专用工作流线程池上异步执行（非 ForkJoinPool.commonPool�?
        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            RunContext run = RunContext.install(req.getUserId(), req.getConversationId(), req.getAgentCode(), threadId);
            run.setStreamSink(sink);
            run.setCardEmitter(buildCardEmitter(threadId, sink));
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

                tryEmit(sink, WorkflowStreamEvent.workflowComplete(status, finalResponse, null, nodeExecutions));
            } catch (Exception e) {
                log.error("Workflow stream execution failed: workflowCode={}, error={}", workflow.getCode(), e.getMessage(), e);
                tryEmit(sink, WorkflowStreamEvent.workflowComplete(
                        org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum.FAILED, null, null, List.of(), e.getMessage()));
            } finally {
                RunContext.clear();
                sink.tryEmitComplete();
                // 从运行注册表中移�?
                if (runId != null) {
                    runningExecutions.remove(runId);
                }
            }
        }, workflowExecutor);

        // 注册以支持取�?
        if (runId != null) {
            runningExecutions.put(runId, executionFuture);
        }

        return sink.asFlux();
    }

    /**
     * 注册可取消的后台执行（试跑异步启动时使用）�?
     */
    public void registerRunningExecution(Long runId, CompletableFuture<Void> future) {
        if (runId == null || future == null) {
            return;
        }
        runningExecutions.put(runId, future);
    }

    /**
     * �?run ID 取消正在运行的工作流执行�?
     *
     * @param runId agent_workflow_run ID
     * @return 找到并取消则�?true；未找到或已完成则为 false
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
     * 获取编译缓存大小，用于健康监控�?
     */
    public int getCompileCacheSize() {
        return compileCache.size();
    }

    /**
     * 获取正在运行的执行数量，用于健康监控�?
     */
    public int getRunningExecutionCount() {
        return runningExecutions.size();
    }

    /**
     * 使用感知流式的节点包装器编译工作流，并向 sink 推送事件�?
     */
    private CompiledGraph<WorkflowState> compileWithStream(GraphSpec workflow, TraceConfig traceConfig,
                                                           Sinks.Many<WorkflowStreamEvent> sink) {
        return doCompile(workflow, traceConfig, sink);
    }

    // ===== JSON 解析 =====

    /**
     * �?definition JSON 字符串解析为 Map�?
     */
    private Map<String, Object> parseDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(definitionJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse workflow definition JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid workflow definition JSON: " + e.getMessage());
        }
    }
}

