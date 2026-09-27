package org.deepstack.ai.engine.node;

import org.deepstack.ai.engine.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 人工确认门闩节点（HITL）。
 * <p>
 * 有待确认卡片且尚无 card_action 时中断；confirm/edit/reject resume 后清空 pending 并放行。
 * 未画本节点时，编译包装器会在下一节点入口按 pending_cards 自动挂起（两者并存）。
 * </p>
 */
@Slf4j
public class CardGateNode implements AsyncNodeAction<WorkflowState>, InterruptableAction<WorkflowState> {

    private final boolean requirePendingCards;

    public CardGateNode(Map<String, Object> properties) {
        Object req = properties != null ? properties.get("requirePendingCards") : null;
        // 默认 true：无 pending 时不打断，避免 resume 后再次挂起
        if (req == null) {
            this.requirePendingCards = true;
        } else {
            this.requirePendingCards = Boolean.TRUE.equals(req)
                    || "true".equalsIgnoreCase(String.valueOf(req));
        }
    }

    /** 执行节点逻辑并返回状态增量。 */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        Map<String, Object> updates = new LinkedHashMap<>();
        Object action = state.value(WorkflowState.CARD_ACTION).orElse(null);
        if (action != null && StringUtils.hasText(String.valueOf(action))) {
            // resume 后清理门闩状态，避免下游再看到 pending / 再次命中 card_action
            updates.put(WorkflowState.PENDING_CARDS, new ArrayList<>());
            updates.put(WorkflowState.CARD_ACTION, "");
            log.info("CardGateNode apply after resume: card_action={}", action);
        } else {
            log.info("CardGateNode pass-through (pre-interrupt or empty)");
        }
        return CompletableFuture.completedFuture(updates);
    }

    /** 判断是否需要 HITL 挂起。 */
    @Override
    public Optional<InterruptionMetadata<WorkflowState>> interrupt(String nodeId, WorkflowState state,
                                                                   RunnableConfig config) {
        return HitlInterrupts.ifAwaitingConfirm(nodeId, state, config, requirePendingCards);
    }
}
