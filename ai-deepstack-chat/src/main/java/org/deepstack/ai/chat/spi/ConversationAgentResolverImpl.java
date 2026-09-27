package org.deepstack.ai.chat.spi;

import org.deepstack.ai.card.spi.ConversationAgentResolver;
import org.deepstack.ai.chat.model.entity.ChatConversation;
import org.deepstack.ai.chat.service.ChatConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按会话 ID 解析智能体编码（卡片 SPI 实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationAgentResolverImpl implements ConversationAgentResolver {

    private final ChatConversationService chatConversationService;

    /**
     * 根据 conversationId 查会话并返回 agentCode；空 ID 或不存在时返回 null。
     */
    @Override
    public String resolveAgentCode(String conversationId) {
        log.info("resolveAgentCode: conversationId={}", conversationId);
        if (conversationId == null || conversationId.isBlank()) {
            log.warn("resolveAgentCode 跳过: conversationId 为空");
            return null;
        }
        ChatConversation conversation = chatConversationService.getByConversationId(conversationId);
        if (conversation == null) {
            log.warn("resolveAgentCode: 会话不存在 conversationId={}", conversationId);
            return null;
        }
        return conversation.getAgentCode();
    }
}
