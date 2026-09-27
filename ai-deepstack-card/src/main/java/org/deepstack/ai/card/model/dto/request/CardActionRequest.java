package org.deepstack.ai.card.model.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 卡片动作请求
 * <p>
 * 用户对卡片执行 confirm / edit / reject 动作时提交。
 * action=edit 时需携带 modifiedPayload；confirm/reject 不需要。
 * </p>
 */
@Data
public class CardActionRequest implements Serializable {

    /**
     * 卡片业务 UUID
     */
    private String cardId;

    /**
     * LangGraph4j thread ID（来自卡片原样回传）
     */
    private String threadId;

    /**
     * LangGraph4j checkpoint ID（来自卡片原样回传）
     */
    private String checkpointId;

    /**
     * 动作类型码：见 CardActionTypeEnum（0确认 / 1编辑 / 2拒绝）
     */
    private Integer action;

    /**
     * 修改后的 payload（仅 action=edit 时非空）
     */
    private Map<String, Object> modifiedPayload;

    /**
     * 用户 ID（勿传；由服务端从登录态覆盖写入）。
     */
    private String userId;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 智能体编码（可选）。
     * <p>
     * 当卡片落库时 conversationId 为空、或会话记录缺失时，用于兜底解析 modelId，
     * 保证 reject 重新生成仍能调用 AI。
     * </p>
     */
    private String agentCode;
}
