package org.deepstack.ai.chat.model.dto.request;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 对话请求
 *
 */
@Data
public class ChatRequest implements Serializable {

    /**
     * 智能体编码（必填），对应 ai_agent.agent_code
     */
    private String agentCode;

    /**
     * 用户ID，用于加载用户级全域提示词（L2 用户画像）；为空时不加载用户提示词
     */
    private String userId;

    /**
     * 会话ID，用于关联上下文记忆；为空时新建会话
     */
    private String conversationId;

    /**
     * 用户消息内容
     */
    private String message;
}
