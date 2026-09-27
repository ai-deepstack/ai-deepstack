package org.deepstack.ai.infra.llm;

import org.deepstack.ai.aimodel.event.AiModelChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型配置变更后清理 Chat / Embedding 客户端缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelClientCacheEvictListener {

    private final AiChatClientFactory aiChatClientFactory;
    private final EmbeddingClientFactory embeddingClientFactory;

    /** 模型配置变更后清理 Chat/Embedding 缓存。 */
    @EventListener
    public void onModelChanged(AiModelChangedEvent event) {
        if (event == null) {
            return;
        }
        if (!StringUtils.hasText(event.modelCode())) {
            log.info("AiModelChangedEvent: modelCode 空，全量 evict");
            aiChatClientFactory.evictAll();
            embeddingClientFactory.evictAll();
            return;
        }
        String code = event.modelCode().trim();
        log.info("AiModelChangedEvent: evict modelCode={}", code);
        aiChatClientFactory.evict(code);
        embeddingClientFactory.evict(code);
    }
}
