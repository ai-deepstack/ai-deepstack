package org.deepstack.ai.tool.catalog;

import org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum;

/**
 * 工具目录条目（无持久化类型泄漏；供 Catalog / Index / Resolver 共用）。
 *
 * @param id                主键
 * @param toolCode          平台工具编码
 * @param toolName          显示名
 * @param description       说明（供 LLM；可空）
 * @param handlerBean       LOCAL 时的 Spring Bean 名；MCP 可空
 * @param enabled           是否启用
 * @param sourceType        见 {@link ToolSourceTypeEnum}
 * @param mcpConnectionCode MCP 连接编码（仅 MCP）
 * @param mcpToolName       MCP 远端工具原名（仅 MCP）
 */
public record ToolCatalogEntry(
        Long id,
        String toolCode,
        String toolName,
        String description,
        String handlerBean,
        boolean enabled,
        Integer sourceType,
        String mcpConnectionCode,
        String mcpToolName
) {
    /**
     * 是否来自 MCP。
     *
     * @return true 表示 MCP 工具
     */
    public boolean mcp() {
        return sourceType != null && sourceType == ToolSourceTypeEnum.MCP.getCode();
    }
}
