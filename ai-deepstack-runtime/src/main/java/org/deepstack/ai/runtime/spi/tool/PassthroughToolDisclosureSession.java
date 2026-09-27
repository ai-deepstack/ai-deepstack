package org.deepstack.ai.runtime.spi.tool;

import org.deepstack.ai.runtime.spi.ToolPort;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * disclosureMode=OFF：不暴露元工具；{@link #exposedToolCallbacks()} 对允许集全量 resolve（与历史行为一致）。
 */
final class PassthroughToolDisclosureSession implements ToolDisclosureSession {

    private final ToolPort toolPort;
    private final ToolDescriptorIndex indexView;
    private final List<String> allowToolCodes;
    private final Set<String> loaded = new LinkedHashSet<>();

    PassthroughToolDisclosureSession(ToolPort toolPort,
                                     ToolDescriptorIndex indexView,
                                     Collection<String> allowToolCodes) {
        this.toolPort = toolPort;
        this.indexView = indexView;
        this.allowToolCodes = allowToolCodes == null
                ? List.of()
                : List.copyOf(allowToolCodes.stream().filter(c -> c != null && !c.isBlank()).toList());
    }

    /** 返回元工具回调。 */
    @Override
    public List<ToolCallback> metaToolCallbacks() {
        return List.of();
    }

    /** 按关键词搜索工具。 */
    @Override
    public List<ToolSearchHit> search(String query, int topK) {
        return indexView.search(new ToolSearchQuery(query, topK, Set.copyOf(allowToolCodes)));
    }

    /** 按编码加载工具描述。 */
    @Override
    public List<ToolDescriptor> load(List<String> toolCodes) {
        if (toolCodes == null || toolCodes.isEmpty()) {
            return List.of();
        }
        Set<String> allow = Set.copyOf(allowToolCodes);
        List<ToolDescriptor> out = new ArrayList<>();
        for (String code : toolCodes) {
            if (code == null || !allow.contains(code)) {
                continue;
            }
            loaded.add(code);
            indexView.get(code).ifPresentOrElse(out::add,
                    () -> out.add(ToolDescriptor.summaryOf(code, code, "", List.of(), null)));
        }
        return out;
    }

    /** 返回本轮暴露给模型的工具回调。 */
    @Override
    public List<ToolCallback> exposedToolCallbacks() {
        return toolPort.resolveToolCallbacksByCodes(allowToolCodes);
    }

    /** 返回已加载的工具编码。 */
    @Override
    public Set<String> loadedToolCodes() {
        return Set.copyOf(loaded);
    }
}
