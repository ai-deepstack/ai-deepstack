package org.deepstack.ai.engine.node;

import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * HITL 中断判定工具：有待确认卡片且尚无用户动作时挂起图运行。
 * <p>
 * 供图节点在写出 pending_cards 后调用，决定是否返回 {@code WAITING_HUMAN} 中断元数据。
 * </p>
 */
@Slf4j
public final class HitlInterrupts {

    /** 工具类，禁止实例化。 */
    private HitlInterrupts() {
    }

    /**
     * 判断状态中是否已有用户卡片动作（如确认/拒绝）。
     *
     * @param state 工作流状态
     * @return 存在非空 {@link WorkflowState#CARD_ACTION} 时为 true
     */
    public static boolean hasCardAction(WorkflowState state) {
        Object action = state.value(WorkflowState.CARD_ACTION).orElse(null);
        return action != null && StringUtils.hasText(String.valueOf(action));
    }

    /**
     * 判断状态中是否存在待确认卡片列表。
     *
     * @param state 工作流状态
     * @return {@link WorkflowState#PENDING_CARDS} 为非空 List 时为 true
     */
    public static boolean hasPendingCards(WorkflowState state) {
        Object pending = state.value(WorkflowState.PENDING_CARDS).orElse(List.of());
        return pending instanceof List<?> list && !list.isEmpty();
    }

    /**
     * 无 card_action 且（不要求 pending，或 pending 非空）时返回 WAITING_HUMAN 中断。
     * <p>
     * 已有用户动作，或要求 pending 却无卡片时跳过中断，继续节点后续逻辑。
     * </p>
     *
     * @param nodeId              当前节点 ID（写入中断元数据）
     * @param state               工作流状态
     * @param config              运行配置（取 threadId 打日志）；可为 null
     * @param requirePendingCards true 时仅在有 pending_cards 才中断
     * @return 中断元数据；不应挂起时为空 Optional
     */
    public static Optional<InterruptionMetadata<WorkflowState>> ifAwaitingConfirm(
            String nodeId, WorkflowState state, RunnableConfig config, boolean requirePendingCards) {
        // 用户已提交动作：不再挂起，交由节点消费 card_action
        if (hasCardAction(state)) {
            log.info("HITL skip interrupt: card_action present, nodeId={}", nodeId);
            return Optional.empty();
        }
        // 要求有待确认卡片却没有：说明无需人工，跳过中断
        if (requirePendingCards && !hasPendingCards(state)) {
            log.info("HITL skip interrupt: no pending_cards, nodeId={}", nodeId);
            return Optional.empty();
        }
        log.info("HITL interrupt WAITING_HUMAN: nodeId={}, threadId={}",
                nodeId, config != null ? config.threadId().orElse(null) : null);
        return Optional.of(InterruptionMetadata.<WorkflowState>builder(nodeId, state)
                .addMetadata("reason", GraphRunStatusEnum.WAITING_HUMAN.name())
                .build());
    }
}
