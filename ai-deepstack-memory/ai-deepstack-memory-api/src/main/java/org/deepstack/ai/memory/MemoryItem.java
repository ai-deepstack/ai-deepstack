package org.deepstack.ai.memory;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 长期记忆条目。
 */
public record MemoryItem(
        String id,
        String userId,
        String agentCode,
        String category,
        String content,
        Double score,
        Map<String, Object> metadata,
        LocalDateTime createTime
) {
}
