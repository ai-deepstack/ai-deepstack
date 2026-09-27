package org.deepstack.ai.card.spi;

/**
 * 按会话 ID 解析智能体编码（SPI，避免 card 模块硬依赖 chat）。
 * <p>由 {@code ai-deepstack-chat} 模块实现。</p>
 */
public interface ConversationAgentResolver {

    /**
     * 根据会话 ID 解析智能体编码。
     *
     * @param conversationId 业务会话 ID
     * @return 智能体编码；未知或不存在时 null
     */
    String resolveAgentCode(String conversationId);
}
