package org.deepstack.ai.tool.catalog;

import org.deepstack.ai.tool.model.entity.AiTool;
import org.deepstack.ai.tool.service.AiToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于 {@link AiToolService} 的工具目录实现（读 {@code ai_tool}）。
 */
@Component
@RequiredArgsConstructor
public class DbToolCatalog implements ToolCatalog {

    private final AiToolService aiToolService;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ToolCatalogEntry> findByCode(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return Optional.empty();
        }
        AiTool tool = aiToolService.getByToolCode(toolCode);
        return Optional.ofNullable(toEntry(tool));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ToolCatalogEntry> listByIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        List<AiTool> tools = aiToolService.listByIds(toolIds);
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<ToolCatalogEntry> out = new ArrayList<>(tools.size());
        for (AiTool t : tools) {
            ToolCatalogEntry e = toEntry(t);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ToolCatalogEntry> listByCodes(List<String> toolCodes) {
        if (toolCodes == null || toolCodes.isEmpty()) {
            return List.of();
        }
        List<AiTool> tools = aiToolService.listEnabledByCodes(toolCodes);
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        // 按入参顺序组装，便于调用方稳定
        java.util.Map<String, AiTool> byCode = new java.util.HashMap<>();
        for (AiTool t : tools) {
            if (t != null && t.getToolCode() != null) {
                byCode.put(t.getToolCode(), t);
            }
        }
        List<ToolCatalogEntry> out = new ArrayList<>(toolCodes.size());
        for (String code : toolCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            AiTool t = byCode.get(code.trim());
            ToolCatalogEntry e = toEntry(t);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ToolCatalogEntry> listEnabled() {
        List<AiTool> tools = aiToolService.lambdaQuery()
                .eq(AiTool::getEnabled, 1)
                .list();
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<ToolCatalogEntry> out = new ArrayList<>(tools.size());
        for (AiTool t : tools) {
            ToolCatalogEntry e = toEntry(t);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * 将 {@link AiTool} 转为目录条目；{@code null} 入参返回 {@code null}。
     *
     * @param tool 工具实体
     * @return 目录条目
     */
    private static ToolCatalogEntry toEntry(AiTool tool) {
        if (tool == null) {
            return null;
        }
        boolean enabled = tool.getEnabled() == null || tool.getEnabled() == 1;
        Integer sourceType = tool.getSourceType() != null
                ? tool.getSourceType()
                : org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum.LOCAL.getCode();
        return new ToolCatalogEntry(
                tool.getId(),
                tool.getToolCode(),
                tool.getToolName(),
                tool.getDescription(),
                tool.getHandlerBean(),
                enabled,
                sourceType,
                tool.getMcpConnectionCode(),
                tool.getMcpToolName()
        );
    }
}
