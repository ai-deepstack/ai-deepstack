package org.deepstack.ai.runtime.spi.tool;

/**
 * 工具检索命中（短卡片，不含完整 inputSchema）。
 */
public record ToolSearchHit(
        String toolCode,
        String name,
        String summary,
        double score
) {
}
