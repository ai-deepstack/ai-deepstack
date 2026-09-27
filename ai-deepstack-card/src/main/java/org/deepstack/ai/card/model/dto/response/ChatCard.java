package org.deepstack.ai.card.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 对话卡片 DTO（对外）
 * <p>
 * 对话流中产出的结构化卡片，由前端按 cardType 渲染。
 * 用户可对卡片执行 confirm / edit / reject 动作。
 * </p>
 *
 */
@Data
public class ChatCard implements Serializable {

    /**
     * 业务 UUID，用户动作回传用
     */
    private String cardId;

    /**
     * 卡片类型（业务自定义）
     */
    private String cardType;

    /**
     * 卡片标题
     */
    private String title;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * LangGraph4j thread ID（不透明 token，前端原样回传）
     */
    private String threadId;

    /**
     * LangGraph4j checkpoint ID（不透明 token，前端原样回传）
     */
    private String checkpointId;

    /**
     * 结构化数据（按 cardType 解释，前端按 cardType 渲染）
     */
    private Map<String, Object> payload;

    /**
     * 支持的动作列表：[{type, label}, ...]
     */
    private List<Map<String, Object>> actions;

    /**
     * 父卡片 ID（reject/edit 后新卡片指向原卡片）
     */
    private String parentCardIdStr;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
