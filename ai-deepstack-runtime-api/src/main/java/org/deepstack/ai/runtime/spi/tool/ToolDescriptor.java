package org.deepstack.ai.runtime.spi.tool;

import java.util.List;

/**
 * 索引中的工具描述；search 命中默认不带完整 inputSchema。
 */
public record ToolDescriptor(
        String toolCode,
        String name,
        String summary,
        List<String> tags,
        String riskTier,
        String inputSchemaJson
) {
    public ToolDescriptor {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** 摘要形态（无 schema）。 */
    public static ToolDescriptor summaryOf(String toolCode, String name, String summary,
                                           List<String> tags, String riskTier) {
        return new ToolDescriptor(toolCode, name, summary, tags, riskTier, null);
    }
}
