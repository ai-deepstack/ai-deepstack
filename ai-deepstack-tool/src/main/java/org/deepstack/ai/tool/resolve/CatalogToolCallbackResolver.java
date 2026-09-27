package org.deepstack.ai.tool.resolve;

import org.deepstack.ai.tool.catalog.ToolCatalog;
import org.deepstack.ai.tool.catalog.ToolCatalogEntry;
import org.deepstack.ai.tool.mcp.McpToolBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LOCAL：handlerBean → {@link ToolCallback}；MCP：{@link McpToolBridge}。
 * 不碰 DB，目录元数据来自 {@link ToolCatalog}。
 * <p>
 * 类名刻意避开 Spring AI 的 {@code toolCallbackResolver} Bean，避免启动冲突。
 */
@Slf4j
@Component
public class CatalogToolCallbackResolver {

    private final ApplicationContext applicationContext;
    private final ToolCatalog toolCatalog;
    private final McpToolBridge mcpToolBridge;

    /** handlerBean 名 → Spring Bean 实例缓存 */
    private final ConcurrentHashMap<String, Object> beanCache = new ConcurrentHashMap<>();

    /** toolCode → 已解析的 ToolCallback 列表缓存 */
    private final ConcurrentHashMap<String, List<ToolCallback>> toolCodeCache = new ConcurrentHashMap<>();

    /**
     * 构造解析器。
     *
     * @param applicationContext Spring 上下文（解析 LOCAL handlerBean）
     * @param toolCatalog        工具目录
     * @param mcpToolBridge      MCP 工具桥接
     */
    public CatalogToolCallbackResolver(ApplicationContext applicationContext,
                                       ToolCatalog toolCatalog,
                                       McpToolBridge mcpToolBridge) {
        this.applicationContext = applicationContext;
        this.toolCatalog = toolCatalog;
        this.mcpToolBridge = mcpToolBridge;
        log.info("CatalogToolCallbackResolver initialized");
    }

    /**
     * 按多个 toolCode 批量解析 {@link ToolCallback}。
     * <p>
     * 已缓存的直接命中；未命中集合一次 {@link ToolCatalog#listByCodes} 拉取再 build，
     * 避免 evictAll 后冷启动 N 次单条查库。
     * </p>
     *
     * @param toolCodes 工具编码列表
     * @return 不可变回调列表；空入参返回空列表
     */
    public List<ToolCallback> resolveByCodes(List<String> toolCodes) {
        if (toolCodes == null || toolCodes.isEmpty()) {
            return List.of();
        }
        log.debug("CatalogToolCallbackResolver: resolveByCodes size={}, codes={}",
                toolCodes.size(), toolCodes);

        List<String> ordered = new ArrayList<>(toolCodes.size());
        List<String> misses = new ArrayList<>();
        for (String code : toolCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String trimmed = code.trim();
            ordered.add(trimmed);
            if (!toolCodeCache.containsKey(trimmed)) {
                misses.add(trimmed);
            }
        }

        if (!misses.isEmpty()) {
            log.info("CatalogToolCallbackResolver: 冷路径批量查目录 misses={}", misses.size());
            List<ToolCatalogEntry> entries = toolCatalog.listByCodes(misses);
            java.util.Map<String, ToolCatalogEntry> byCode = new java.util.HashMap<>();
            for (ToolCatalogEntry e : entries) {
                if (e != null && e.toolCode() != null) {
                    byCode.put(e.toolCode(), e);
                }
            }
            for (String code : misses) {
                // computeIfAbsent 保证并发下只 build 一次
                toolCodeCache.computeIfAbsent(code, c -> {
                    ToolCatalogEntry entry = byCode.get(c);
                    if (entry == null || !entry.enabled()) {
                        log.warn("CatalogToolCallbackResolver: batch miss/disabled toolCode={}", c);
                        return List.of();
                    }
                    ToolCallback cb = buildOne(entry);
                    return cb == null ? List.of() : List.of(cb);
                });
            }
        }

        List<ToolCallback> result = new ArrayList<>(ordered.size());
        for (String code : ordered) {
            List<ToolCallback> cbs = toolCodeCache.get(code);
            if (cbs != null && !cbs.isEmpty()) {
                result.addAll(cbs);
            } else {
                log.warn("CatalogToolCallbackResolver: resolve miss toolCode={}", code);
            }
        }
        log.debug("CatalogToolCallbackResolver: resolveByCodes done requested={}, resolved={}, coldMisses={}",
                toolCodes.size(), result.size(), misses.size());
        return Collections.unmodifiableList(result);
    }

    /**
     * 按单个 toolCode 解析 {@link ToolCallback}（带缓存）。
     *
     * @param toolCode 工具编码
     * @return 回调列表；未命中或未启用时为空列表
     */
    public List<ToolCallback> resolveByCode(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return List.of();
        }
        List<ToolCallback> callbacks = toolCodeCache.computeIfAbsent(toolCode, this::buildForCode);
        if (callbacks.isEmpty()) {
            log.warn("CatalogToolCallbackResolver: resolve miss toolCode={}", toolCode);
        }
        return callbacks;
    }

    /**
     * 驱逐指定 toolCode 的回调缓存。
     *
     * @param toolCode 工具编码
     */
    public void evict(String toolCode) {
        if (toolCode != null) {
            toolCodeCache.remove(toolCode);
        }
        log.info("CatalogToolCallbackResolver: evicted caches for toolCode={}", toolCode);
    }

    /**
     * 清空全部 toolCode 回调缓存。
     */
    public void evictAll() {
        toolCodeCache.clear();
        log.info("CatalogToolCallbackResolver: evicted all caches");
    }

    /**
     * 从目录查找启用条目并构建回调列表。
     *
     * @param toolCode 工具编码
     * @return 0 或 1 个回调的列表
     */
    private List<ToolCallback> buildForCode(String toolCode) {
        return toolCatalog.findByCode(toolCode)
                .filter(ToolCatalogEntry::enabled)
                .map(this::buildOne)
                .map(cb -> cb == null ? List.<ToolCallback>of() : List.of(cb))
                .orElse(List.of());
    }

    /**
     * 按目录条目构建单个 {@link ToolCallback}（MCP 或 LOCAL）。
     * <p>
     * 若库表有 description，会包装为覆盖描述的回调。
     *
     * @param entry 目录条目
     * @return ToolCallback；解析失败返回 {@code null}
     */
    private ToolCallback buildOne(ToolCatalogEntry entry) {
        if (entry.mcp()) {
            log.debug("CatalogToolCallbackResolver: build MCP toolCode={}, connection={}, remote={}",
                    entry.toolCode(), entry.mcpConnectionCode(), entry.mcpToolName());
            try {
                ToolCallback cb = mcpToolBridge.resolve(
                        entry.mcpConnectionCode(),
                        entry.mcpToolName(),
                        entry.toolCode());
                if (cb == null) {
                    log.warn("CatalogToolCallbackResolver: MCP resolve miss toolCode={}, connection={}, remote={}",
                            entry.toolCode(), entry.mcpConnectionCode(), entry.mcpToolName());
                    return null;
                }
                // 优先使用库表 description 覆盖远端默认描述
                String dbDesc = entry.description();
                if (dbDesc != null && !dbDesc.isBlank()) {
                    return new DescriptionOverridingToolCallback(cb, dbDesc);
                }
                return cb;
            } catch (Exception e) {
                log.warn("CatalogToolCallbackResolver: MCP resolve failed toolCode={}, connection={}: {}",
                        entry.toolCode(), entry.mcpConnectionCode(), e.getMessage(), e);
                return null;
            }
        }
        log.debug("CatalogToolCallbackResolver: build LOCAL toolCode={}, handlerBean={}",
                entry.toolCode(), entry.handlerBean());
        Object bean = resolveBean(entry.handlerBean());
        if (bean == null) {
            log.warn("CatalogToolCallbackResolver: handler bean not found: handlerBean={}, toolCode={}",
                    entry.handlerBean(), entry.toolCode());
            return null;
        }
        ToolCallback[] callbacks = ToolCallbacks.from(bean);
        if (callbacks == null || callbacks.length == 0) {
            log.warn("CatalogToolCallbackResolver: no @Tool methods on bean: handlerBean={}, toolCode={}",
                    entry.handlerBean(), entry.toolCode());
            return null;
        }
        // LOCAL：取 Bean 上第一个 @Tool 方法
        ToolCallback cb = callbacks[0];
        String dbDesc = entry.description();
        if (dbDesc != null && !dbDesc.isBlank()) {
            return new DescriptionOverridingToolCallback(cb, dbDesc);
        }
        return cb;
    }

    /**
     * 按 Bean 名从 Spring 容器解析 handler（带缓存）。
     *
     * @param handlerBean Bean 名称
     * @return Bean 实例；找不到或失败返回 {@code null}
     */
    private Object resolveBean(String handlerBean) {
        if (handlerBean == null || handlerBean.isBlank()) {
            return null;
        }
        return beanCache.computeIfAbsent(handlerBean, name -> {
            try {
                return applicationContext.getBean(name);
            } catch (NoSuchBeanDefinitionException e) {
                log.warn("CatalogToolCallbackResolver: bean not found in context: {}", name);
                return null;
            } catch (Exception e) {
                log.warn("CatalogToolCallbackResolver: failed to resolve bean {}: {}", name, e.getMessage());
                return null;
            }
        });
    }

    /**
     * 装饰器：保留委托的 name / inputSchema，用库表描述覆盖 description。
     */
    private static final class DescriptionOverridingToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final String descriptionOverride;
        private volatile ToolDefinition overriddenDefinition;

        /**
         * @param delegate             原始回调
         * @param descriptionOverride  覆盖用描述
         */
        DescriptionOverridingToolCallback(ToolCallback delegate, String descriptionOverride) {
            this.delegate = delegate;
            this.descriptionOverride = descriptionOverride;
        }

        /**
         * @return 覆盖 description 后的工具定义（懒构建并缓存）
         */
        @Override
        public ToolDefinition getToolDefinition() {
            ToolDefinition d = overriddenDefinition;
            if (d == null) {
                synchronized (this) {
                    if (overriddenDefinition == null) {
                        ToolDefinition orig = delegate.getToolDefinition();
                        overriddenDefinition = new ToolDefinition() {
                            /** 工具名。 */
                            @Override
                            public String name() {
                                return orig.name();
                            }

                            /** 工具说明。 */
                            @Override
                            public String description() {
                                return descriptionOverride;
                            }

                            /** 工具入参 JSON Schema。 */
                            @Override
                            public String inputSchema() {
                                return orig.inputSchema();
                            }
                        };
                    }
                    d = overriddenDefinition;
                }
            }
            return d;
        }

        /**
         * @param toolInput 工具入参 JSON
         * @return 委托执行结果
         */
        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput);
        }

        /**
         * @param toolInput   工具入参 JSON
         * @param toolContext Spring AI 工具上下文
         * @return 委托执行结果
         */
        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return delegate.call(toolInput, toolContext);
        }
    }
}
