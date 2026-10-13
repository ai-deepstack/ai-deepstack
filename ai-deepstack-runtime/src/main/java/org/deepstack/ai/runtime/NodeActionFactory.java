package org.deepstack.ai.runtime;

import org.deepstack.ai.engine.api.TraceConfig;
import org.deepstack.ai.engine.api.WorkflowState;
import org.deepstack.ai.engine.api.WorkflowStreamEvent;
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
import org.deepstack.ai.engine.support.NodeTimeoutConfig;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.observability.AgentRunMetricNames;
import org.deepstack.ai.runtime.observability.NodeExecutionRecords;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 节点动作工厂：按节点类型创建 {@link AsyncNodeAction}，并叠加超时守卫与执行跟踪包装。
 * <p>
 * 承担节点执行期的横切关注点：写 NODE_EXECUTIONS、按需推流 NODE_START / NODE_COMPLETE、
 * 收集 pending_cards（{@link NodeActionFactory#mergePendingCardsIntoUpdate}）、上报节点耗时指标，
 * 以及 HITL 门闩装配（{@link HitlAwareNodeAction}）。图结构与缓存不归本类管（见 {@link WorkflowGraphCompiler}）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeActionFactory {

    private final AgentNodeContext agentNodeContext;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    /**
     * 按节点类型创建 NodeAction，对需要服务的节点注入 AgentNodeContext。
     */
    @SuppressWarnings("unchecked")
    public AsyncNodeAction<WorkflowState> createNodeAction(String type, Map<String, Object> properties) {
        return createNodeAction(type, properties, null, null);
    }

    /**
     * 按节点类型创建 NodeAction，对需要服务的节点注入 AgentNodeContext。
     *
     * @param type       节点类型编码
     * @param properties 节点属性
     * @param nodeId     节点 ID（llm-node 等需要）
     * @param nodeName   节点显示名
     */
    @SuppressWarnings("unchecked")
    public AsyncNodeAction<WorkflowState> createNodeAction(String type, Map<String, Object> properties,
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

    /**
     * 为节点动作包装超时守卫。
     * 若委托未在配置超时内完成，CompletableFuture 将以 TimeoutException 异常完成。
     * 跟踪包装器会捕获并记录 FAILED 执行。
     *
     * @param nodeId        节点标识（用于日志）
     * @param delegate      原始节点动作
     * @param timeoutConfig 节点级超时配置
     * @return 带超时守卫的节点动作
     */
    public AsyncNodeAction<WorkflowState> wrapWithTimeout(String nodeId,
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
                        // 其他异常：按 RuntimeException 包装后重新抛出
                        throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
                    });
        };
    }

    /**
     * 按 TraceConfig 为节点动作包装执行跟踪。
     * <ul>
     *   <li>NONE：透传，不记录也不推流。</li>
     *   <li>RECORD：将 nodeId、name、type、input、output、durationMs、status 写入 NODE_EXECUTIONS。</li>
     *   <li>STREAM：RECORD + 通过 sink 推送中间进度事件（sink 非空且 shouldStream 时）。</li>
     * </ul>
     *
     * @param sink 可为 null；为 null 或 {@code !traceConfig.shouldStream()} 时不推送 NODE_START / NODE_COMPLETE
     */
    public AsyncNodeAction<WorkflowState> wrapWithTracking(String nodeId, String name, String type,
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
                // 节点可能被调度到安装上下文之外的线程；绑定后再进入委托与完成回调，
                // 使节点内 RunContext.current()（例如出卡落库取 threadId）始终取得到值
                RunContext nodeCtx = runContextFor(state);
                return RunContext.bound(nodeCtx, () -> delegate.apply(state)).handle((output, error) ->
                        RunContext.bound(nodeCtx, () -> {
                            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                            if (error != null) {
                                drainPendingCardsFor(state);
                                recordNodeDuration(stateString(state, WorkflowState.AGENT_CODE), type,
                                        GraphRunStatusEnum.FAILED.name(), durationMs);
                                return (Object) error;
                            }
                            recordNodeDuration(stateString(state, WorkflowState.AGENT_CODE), type,
                                    GraphRunStatusEnum.SUCCESS.name(), durationMs);
                            Map<String, Object> update = new LinkedHashMap<>(output != null ? output : Map.of());
                            mergePendingCardsIntoUpdate(state, update);
                            clearResumeCardAction(state, type, update);
                            return (Object) update;
                        })
                ).thenCompose(result -> {
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
                String inputSnapshot = traceConfig.shouldRecord() ? WorkflowStateSummarizer.summarizeState(state) : "";

                if (emitStream) {
                    WorkflowCardEmitter.tryEmit(sink, WorkflowStreamEvent.nodeStart(nodeId, name, type, inputSnapshot));
                }

                // 不用 join：避免在 workflowExecutor 线程上阻塞等待，嵌套提交时可能死锁
                // 节点可能被调度到安装上下文之外的线程；绑定后再进入委托与完成回调，
                // 使节点内 RunContext.current()（例如出卡落库取 threadId）始终取得到值
                RunContext nodeCtx = runContextFor(state);
                return RunContext.bound(nodeCtx, () -> delegate.apply(state)).handle((output, error) ->
                        RunContext.bound(nodeCtx, () -> {
                            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                            if (error != null) {
                                drainPendingCardsFor(state);
                                recordNodeDuration(stateString(state, WorkflowState.AGENT_CODE), type,
                                        GraphRunStatusEnum.FAILED.name(), durationMs);
                                if (traceConfig.shouldRecord()) {
                                    Map<String, Object> failRec = NodeExecutionRecords
                                            .failed(nodeId, name, type, inputSnapshot, durationMs,
                                                    error.getMessage());
                                    Map<String, Object> failUpdate = new LinkedHashMap<>();
                                    failUpdate.put(WorkflowState.NODE_EXECUTIONS, List.of(failRec));
                                    if (emitStream) {
                                        WorkflowCardEmitter.tryEmit(sink, WorkflowStreamEvent.nodeComplete(
                                                nodeId, name, type, durationMs,
                                                GraphRunStatusEnum.FAILED,
                                                error.getMessage()));
                                    }
                                    // 失败仍通过 thenCompose 抛出；记录靠 appender 无法在失败路径写入时，
                                    // 至少推送 SSE；同步路径依赖异常上抛由编排层记 error_code
                                    log.warn("wrapWithTracking 节点失败: nodeId={}, type={}, durationMs={}, err={}",
                                            nodeId, type, durationMs, error.getMessage());
                                } else if (emitStream) {
                                    WorkflowCardEmitter.tryEmit(sink, WorkflowStreamEvent.nodeComplete(
                                            nodeId, name, type, durationMs,
                                            GraphRunStatusEnum.FAILED,
                                            error.getMessage()));
                                }
                                return (Object) error;
                            }
                            Map<String, Object> update = new LinkedHashMap<>(output != null ? output : Map.of());
                            mergePendingCardsIntoUpdate(state, update);
                            clearResumeCardAction(state, type, update);

                            recordNodeDuration(stateString(state, WorkflowState.AGENT_CODE), type,
                                    GraphRunStatusEnum.SUCCESS.name(), durationMs);
                            if (traceConfig.shouldRecord()) {
                                Map<String, Object> record = NodeExecutionRecords
                                        .success(nodeId, name, type, inputSnapshot,
                                                WorkflowStateSummarizer.summarizeOutput(output), durationMs);
                                update.put(WorkflowState.NODE_EXECUTIONS, List.of(record));
                                log.debug("wrapWithTracking 节点成功: nodeId={}, type={}, durationMs={}",
                                        nodeId, type, durationMs);

                                if (emitStream) {
                                    WorkflowCardEmitter.tryEmit(sink, WorkflowStreamEvent.nodeComplete(
                                            nodeId, name, type, durationMs,
                                            GraphRunStatusEnum.SUCCESS, null));
                                }
                            }
                            return (Object) update;
                        })
                ).thenCompose(result -> {
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
     * @param agentCode  Agent 编码（取自 state，节点可能在其他线程执行）
     * @param nodeType   节点类型
     * @param status     SUCCESS / FAILED
     * @param durationMs 耗时毫秒
     */
    private void recordNodeDuration(String agentCode, String nodeType, String status, long durationMs) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;
        }
        String agent = StringUtils.hasText(agentCode) ? agentCode.trim() : AgentRunMetricNames.UNKNOWN;
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
     * 自动门闩 resume 后清除 card_action，避免后续节点误判「已确认」而不再挂起。
     * 显式 card-gate 的 apply 已负责清理。
     */
    private void clearResumeCardAction(WorkflowState state, String type, Map<String, Object> update) {
        if ("card-gate-node".equals(type) || !HitlInterrupts.hasCardAction(state)) {
            return;
        }
        update.put(WorkflowState.CARD_ACTION, "");
    }

    /** 读取 state 中的字符串值；缺失时返回 null。 */
    private static String stateString(WorkflowState state, String key) {
        return state == null ? null : state.value(key).map(String::valueOf).orElse(null);
    }

    /**
     * 定位本次执行的 RunContext：节点可能被调度到其它线程，ThreadLocal 取不到，
     * 因此按 state 里的 threadId 回查。
     *
     * @param state 节点状态
     * @return 上下文；无对应执行时为 null
     */
    private static RunContext runContextFor(WorkflowState state) {
        return RunContext.currentOrThread(stateString(state, WorkflowState.THREAD_ID));
    }

    /**
     * 取出本次执行缓冲的待确认卡片（按 state 的 threadId 定位）。
     *
     * @param state 节点状态
     * @return 卡片元数据列表；无缓冲时返回空列表
     */
    private static List<Map<String, Object>> drainPendingCardsFor(WorkflowState state) {
        RunContext ctx = runContextFor(state);
        return ctx != null ? ctx.takePendingCards() : List.of();
    }

    /**
     * 将本节点缓冲的出卡写入 {@code pending_cards}（追加）。
     */
    @SuppressWarnings("unchecked")
    private void mergePendingCardsIntoUpdate(WorkflowState state, Map<String, Object> update) {
        List<Map<String, Object>> emitted = drainPendingCardsFor(state);
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
}
