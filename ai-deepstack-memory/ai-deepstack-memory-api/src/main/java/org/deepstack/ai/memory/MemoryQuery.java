package org.deepstack.ai.memory;

/**
 * 长期记忆召回请求。
 */
public record MemoryQuery(
        String userId,
        String agentCode,
        String queryText,
        int limit,
        boolean enableGraph
) {
    public MemoryQuery {
        if (limit < 1) {
            limit = 10;
        }
        if (limit > 50) {
            limit = 50;
        }
        if (queryText == null) {
            queryText = "";
        }
    }
}
