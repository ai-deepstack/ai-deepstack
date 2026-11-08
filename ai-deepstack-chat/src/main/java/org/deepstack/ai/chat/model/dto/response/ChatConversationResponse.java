package org.deepstack.ai.chat.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话历史列表项。
 */
@Data
public class ChatConversationResponse implements Serializable {

    /** 业务会话 ID */
    private String conversationId;

    /** 会话标题（首条用户消息摘要） */
    private String title;

    /** Agent 编码 */
    private String agentCode;

    /** 对话轮数 */
    private Integer messageCount;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
