package org.deepstack.ai.runtime.config;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话通道核心配置
 * <p>
 * ChatMemory 使用 JDBC 持久化（通过 {@link org.deepstack.ai.infra.memory.JdbcChatMemoryRepository}），
 * 服务重启后会话记忆不丢失。
 * ChatModel 和 ChatClient 不再在此构建 —— 由 {@link org.deepstack.ai.infra.llm.AiChatClientFactory} 和
 * {@link org.deepstack.ai.chat.service.impl.ChatServiceImpl} 按请求动态创建。
 * </p>
 *
 */
@Configuration
public class ChatClientConfiguration {

    /**
     * 窗口记忆 Bean（最多保留 50 条消息，JDBC 持久化）。
     */
    @Bean
    public MessageWindowChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(50)
                .build();
    }
}
