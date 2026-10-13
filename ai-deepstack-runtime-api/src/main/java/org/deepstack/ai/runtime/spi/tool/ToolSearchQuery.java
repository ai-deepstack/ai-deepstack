package org.deepstack.ai.runtime.spi.tool;

import java.util.Set;

/**
 * 工具检索请求。{@code allowToolCodes} 为空时不得搜全平台（应返回空）。
 */
public record ToolSearchQuery(
        String text,
        int topK,
        Set<String> allowToolCodes
) {
    public ToolSearchQuery {
        allowToolCodes = allowToolCodes == null ? Set.of() : Set.copyOf(allowToolCodes);
        if (topK <= 0) {
            topK = 8;
        }
    }
}
