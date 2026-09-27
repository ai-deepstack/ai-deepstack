package org.deepstack.ai.memory;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆写入请求。
 */
public record MemoryWrite(
        String userId,
        String agentCode,
        String content,
        String category,
        boolean enableGraph,
        List<String> entities,
        String conversationId,
        Map<String, Object> metadata
) {
    public MemoryWrite {
        if (category == null || category.isBlank()) {
            category = "fact";
        }
        if (entities == null) {
            entities = List.of();
        }
    }
}
