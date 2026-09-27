package org.deepstack.ai.tool.index;

import org.deepstack.ai.runtime.spi.tool.ToolDescriptor;
import org.deepstack.ai.runtime.spi.tool.ToolDescriptorIndex;
import org.deepstack.ai.tool.catalog.ToolCatalog;
import org.deepstack.ai.tool.catalog.ToolCatalogEntry;
import org.deepstack.ai.tool.service.impl.ToolDescriptionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@link ToolCatalog} 启用工具 hydrate 进 {@link ToolDescriptorIndex}（仅摘要，无完整 schema）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolIndexHydrator {

    private final ToolCatalog toolCatalog;
    private final ToolDescriptorIndex toolDescriptorIndex;
    private final ToolDescriptionResolver toolDescriptionResolver;

    /**
     * 应用就绪后执行全量索引刷新。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refreshAll();
    }

    /**
     * 全量刷新 Index（启动 / 工具 CRUD / MCP sync 后）。
     * <p>
     * 从目录读取全部启用工具，转为摘要描述符后整体替换索引。
     */
    public void refreshAll() {
        List<ToolCatalogEntry> entries = toolCatalog.listEnabled();
        List<ToolDescriptor> descriptors = new ArrayList<>(entries.size());
        int mcp = 0;
        int local = 0;
        for (ToolCatalogEntry e : entries) {
            descriptors.add(toDescriptor(e));
            if (e.mcp()) {
                mcp++;
            } else {
                local++;
            }
        }
        toolDescriptorIndex.replaceAll(descriptors);
        log.info("ToolIndexHydrator: hydrated {} tools (local={}, mcp={})",
                descriptors.size(), local, mcp);
    }

    /**
     * 将目录条目转为索引用摘要描述符。
     * <p>
     * 摘要优先库表 description；LOCAL 可回退 Java {@code @Tool} 描述；再回退名称/编码。
     *
     * @param e 目录条目
     * @return 摘要 {@link ToolDescriptor}
     */
    private ToolDescriptor toDescriptor(ToolCatalogEntry e) {
        String summary = e.description();
        if (!StringUtils.hasText(summary) && StringUtils.hasText(e.handlerBean())) {
            // LOCAL：尝试从 handler Bean 的 @Tool 注解解析描述
            summary = toolDescriptionResolver.resolveJavaDescription(e.handlerBean());
        }
        if (!StringUtils.hasText(summary)) {
            summary = e.toolName() != null ? e.toolName() : e.toolCode();
        }
        return ToolDescriptor.summaryOf(
                e.toolCode(),
                e.toolName() != null ? e.toolName() : e.toolCode(),
                summary,
                List.of(),
                null);
    }
}
