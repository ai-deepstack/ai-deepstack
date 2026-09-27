package org.deepstack.ai.memory.pg;

import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.memory.LongTermMemoryStore;
import org.deepstack.ai.memory.MemoryItem;
import org.deepstack.ai.memory.MemoryPort;
import org.deepstack.ai.memory.MemoryQuery;
import org.deepstack.ai.memory.MemoryWrite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link MemoryPort} → {@link LongTermMemoryStore}（PG 混合存储）。
 */
@Slf4j
@RequiredArgsConstructor
public class MemoryPortAdapter implements MemoryPort {

    private final LongTermMemoryStore store;
    private final SysConfigPort sysConfigPort;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean available() {
        return sysConfigPort.isYes(SysConfigKeys.MEMORY_ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String recallContext(String userId, String agentCode, String query, int limit, boolean enableGraph) {
        if (!available() || !StringUtils.hasText(userId)) {
            return "";
        }
        log.debug("MemoryPort.recall: userId={}, agentCode={}, limit={}, enableGraph={}",
                userId, agentCode, limit, enableGraph);
        List<MemoryItem> items = store.recall(new MemoryQuery(userId, agentCode, query, limit, enableGraph));
        String ctx = store.formatContext(items);
        log.info("MemoryPort.recall done: userId={}, hits={}", userId, items != null ? items.size() : 0);
        return ctx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remember(String userId, String agentCode, String content, String category,
                         boolean enableGraph, List<String> entities, String conversationId) {
        if (!available() || !StringUtils.hasText(userId) || !StringUtils.hasText(content)) {
            log.debug("MemoryPort.remember skipped: disabled or blank userId/content");
            return;
        }
        log.info("MemoryPort.remember: userId={}, agentCode={}, category={}, enableGraph={}",
                userId, agentCode, category, enableGraph);
        store.remember(new MemoryWrite(
                userId, agentCode, content, category, enableGraph, entities, conversationId, null));
    }
}
