package org.deepstack.ai.chat.model.dto.internal.response;

import org.deepstack.ai.card.model.dto.response.ChatCard;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 对话响应
 *
 */
@Data
public class ChatResponse implements Serializable {

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * AI 回复内容
     */
    private String content;

    /**
     * 本次对话产出的卡片列表（同步路径；流式路径通过 SSE event=card 推送，本字段为 null）
     */
    private List<ChatCard> cards;

    /**
     * 输入 token 数（来自 LLM usage.prompt_tokens）
     */
    private Integer promptTokens;

    /**
     * 输出 token 数（来自 LLM usage.completion_tokens）
     */
    private Integer completionTokens;

    /**
     * 总 token 数（来自 LLM usage.total_tokens）
     */
    private Integer totalTokens;

    /**
     * 首 token 延迟（毫秒）。同步路径为 null；流式路径为从订阅到第一个 chunk 到达的耗时
     */
    private Long firstTokenLatencyMs;

    /**
     * 总耗时（毫秒）。从 ChatService 进入 chat()/streamChat() 到完成的总耗时
     */
    private Long latencyMs;

    /** GRAPH：状态码（GraphRunStatusEnum） */
    private Integer status;

    /** GRAPH：状态中文名 */
    private String statusName;

    /** GRAPH HITL 恢复用 threadId */
    private String threadId;

    public ChatResponse() {
    }

    public ChatResponse(String conversationId, String content) {
        this.conversationId = conversationId;
        this.content = content;
    }
}
