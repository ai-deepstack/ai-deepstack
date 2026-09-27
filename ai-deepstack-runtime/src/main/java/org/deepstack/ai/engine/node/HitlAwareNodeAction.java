package org.deepstack.ai.engine.node;

import org.deepstack.ai.engine.WorkflowState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 节点包装：保留委托的 {@link InterruptableAction}，并在无显式门闩时按 pending_cards 自动挂起。
 */
public final class HitlAwareNodeAction
        implements AsyncNodeAction<WorkflowState>, InterruptableAction<WorkflowState> {

    private final String nodeType;
    private final AsyncNodeAction<WorkflowState> applyDelegate;
    private final InterruptableAction<WorkflowState> interruptDelegate;
    private final boolean autoInterruptOnPending;

    @SuppressWarnings("unchecked")
    public HitlAwareNodeAction(String nodeType,
                               AsyncNodeAction<WorkflowState> applyDelegate,
                               AsyncNodeAction<WorkflowState> interruptSource,
                               boolean autoInterruptOnPending) {
        this.nodeType = nodeType;
        this.applyDelegate = applyDelegate;
        this.interruptDelegate = interruptSource instanceof InterruptableAction<?> ia
                ? (InterruptableAction<WorkflowState>) ia
                : null;
        this.autoInterruptOnPending = autoInterruptOnPending;
    }

    /** 执行节点逻辑并返回状态增量。 */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        return applyDelegate.apply(state);
    }

    /** 判断是否需要 HITL 挂起。 */
    @Override
    public Optional<InterruptionMetadata<WorkflowState>> interrupt(String nodeId, WorkflowState state,
                                                                   RunnableConfig config) {
        if (interruptDelegate != null) {
            return interruptDelegate.interrupt(nodeId, state, config);
        }
        if (!autoInterruptOnPending || "start-node".equals(nodeType)) {
            return Optional.empty();
        }
        return HitlInterrupts.ifAwaitingConfirm(nodeId, state, config, true);
    }
}
