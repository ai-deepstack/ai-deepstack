package org.deepstack.ai.tool.disclosure;

import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.runtime.spi.tool.ToolDescriptor;
import org.deepstack.ai.runtime.spi.tool.ToolDescriptorIndex;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSession;
import org.deepstack.ai.runtime.spi.tool.ToolSearchHit;
import org.deepstack.ai.runtime.spi.tool.ToolSearchQuery;
import org.deepstack.ai.tool.config.DeepstackToolProperties;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 渐进式工具披露会话：先暴露元工具，经 search/load 后再解析业务工具。
 */
@Slf4j
final class ProgressiveToolDisclosureSession implements ToolDisclosureSession {

    private final ToolPort toolPort;
    private final ToolDescriptorIndex indexView;
    private final List<String> allowToolCodes;
    private final Set<String> allowSet;
    private final int searchTopK;
    private final Set<String> loaded = new LinkedHashSet<>();
    private final List<ToolCallback> metaCallbacks;

    /**
     * @param toolPort       工具解析端口
     * @param indexView      允许集范围内的工具索引视图
     * @param allowToolCodes 绑定允许的工具编码
     * @param sysConfigPort  系统配置（searchTopK 等）
     * @param props          yml 兜底配置
     */
    ProgressiveToolDisclosureSession(ToolPort toolPort,
                                     ToolDescriptorIndex indexView,
                                     Collection<String> allowToolCodes,
                                     SysConfigPort sysConfigPort,
                                     DeepstackToolProperties props) {
        this.toolPort = toolPort;
        this.indexView = indexView;
        this.allowToolCodes = allowToolCodes == null
                ? List.of()
                : List.copyOf(allowToolCodes.stream().filter(c -> c != null && !c.isBlank()).toList());
        this.allowSet = Set.copyOf(this.allowToolCodes);
        int fromCfg = sysConfigPort != null
                ? sysConfigPort.getInt(SysConfigKeys.TOOLS_SEARCH_TOP_K, 0)
                : 0;
        if (fromCfg > 0) {
            this.searchTopK = fromCfg;
        } else {
            this.searchTopK = props != null && props.getSearchTopK() > 0 ? props.getSearchTopK() : 8;
        }
        this.metaCallbacks = List.of(searchMetaTool(), loadMetaTool());
    }

    /** {@inheritDoc} */
    @Override
    public List<ToolCallback> metaToolCallbacks() {
        return metaCallbacks;
    }

    /**
     * 在允许集内搜索工具摘要。
     *
     * @param query 搜索词
     * @param topK  返回条数上限；≤0 时用默认 searchTopK
     * @return 命中列表
     */
    @Override
    public List<ToolSearchHit> search(String query, int topK) {
        int k = topK > 0 ? topK : searchTopK;
        log.info("ProgressiveDisclosure search: query={}, topK={}, allowSize={}", query, k, allowSet.size());
        return indexView.search(new ToolSearchQuery(query, k, allowSet));
    }

    /**
     * 加载指定 toolCode 的完整描述并标记为已暴露。
     *
     * @param toolCodes 待加载编码列表
     * @return 工具描述列表
     */
    @Override
    public List<ToolDescriptor> load(List<String> toolCodes) {
        if (toolCodes == null || toolCodes.isEmpty()) {
            log.warn("ProgressiveDisclosure load 跳过: toolCodes 为空");
            return List.of();
        }
        log.info("ProgressiveDisclosure load: toolCodes={}, allowSize={}", toolCodes, allowSet.size());
        List<ToolDescriptor> out = new ArrayList<>();
        for (String code : toolCodes) {
            if (code == null || !allowSet.contains(code)) {
                log.warn("ProgressiveDisclosure skip load outside allow set: toolCode={}", code);
                continue;
            }
            loaded.add(code);
            indexView.get(code).ifPresentOrElse(out::add,
                    () -> out.add(ToolDescriptor.summaryOf(code, code, "", List.of(), null)));
        }
        return out;
    }

    /** {@inheritDoc} */
    @Override
    public List<ToolCallback> exposedToolCallbacks() {
        List<ToolCallback> out = new ArrayList<>(metaCallbacks);
        if (!loaded.isEmpty()) {
            out.addAll(toolPort.resolveToolCallbacksByCodes(List.copyOf(loaded)));
        }
        log.debug("ProgressiveDisclosure exposed: meta={}, loaded={}", metaCallbacks.size(), loaded.size());
        return out;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> loadedToolCodes() {
        return Set.copyOf(loaded);
    }

    /** 构建 tool_search 元工具回调。 */
    private ToolCallback searchMetaTool() {
        String schema = """
                {"type":"object","properties":{"query":{"type":"string","description":"search query"},"topK":{"type":"integer","description":"result limit"}},"required":["query"]}
                """;
        return new SimpleToolCallback(META_TOOL_SEARCH,
                "Search bound tools; returns short list (code/name/summary). Call load_tools before invoking.",
                schema,
                input -> {
                    String query = "";
                    int topK = searchTopK;
                    try {
                        var obj = JSON.parseObject(input);
                        if (obj != null) {
                            query = obj.getString("query");
                            Integer k = obj.getInteger("topK");
                            if (k != null && k > 0) {
                                topK = k;
                            }
                        }
                    } catch (Exception ignored) {
                        query = input;
                    }
                    List<ToolSearchHit> hits = search(query, topK);
                    return JSON.toJSONString(hits);
                });
    }

    /** 构建 load_tools 元工具回调。 */
    private ToolCallback loadMetaTool() {
        String schema = """
                {"type":"object","properties":{"toolCodes":{"type":"array","items":{"type":"string"},"description":"要加载完整定义的 toolCode 列表"}},"required":["toolCodes"]}
                """;
        return new SimpleToolCallback(META_LOAD_TOOLS,
                "Load full definitions for bound tools (toolCodes from tool_search).",
                schema,
                input -> {
                    List<String> codes = new ArrayList<>();
                    try {
                        var obj = JSON.parseObject(input);
                        if (obj != null) {
                            var arr = obj.getJSONArray("toolCodes");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    String c = arr.getString(i);
                                    if (StringUtils.hasText(c)) {
                                        codes.add(c.trim());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        return "{\"error\":\"invalid args: " + e.getMessage() + "\"}";
                    }
                    List<ToolDescriptor> loadedDesc = load(codes);
                    return JSON.toJSONString(loadedDesc);
                });
    }

    @FunctionalInterface
    private interface ToolInvoker {
        String call(String input);
    }

    private static final class SimpleToolCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final ToolInvoker invoker;

        SimpleToolCallback(String name, String description, String inputSchema, ToolInvoker invoker) {
            this.definition = new ToolDefinition() {
                /** 工具名。 */
                @Override
                public String name() {
                    return name;
                }

                /** 工具说明。 */
                @Override
                public String description() {
                    return description;
                }

                /** 工具入参 JSON Schema。 */
                @Override
                public String inputSchema() {
                    return inputSchema;
                }
            };
            this.invoker = invoker;
        }

        /** 返回 ToolDefinition。 */
        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        /** 执行工具调用。 */
        @Override
        public String call(String toolInput) {
            return invoker.call(toolInput != null ? toolInput : "{}");
        }

        /** 执行工具调用。 */
        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return call(toolInput);
        }
    }
}

