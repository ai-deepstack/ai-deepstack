package org.deepstack.ai.runtime.spi.tool;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存 Index；无 hydrate 时为空。search 为简单子串匹配（后续可换向量）。
 */
public final class InMemoryToolDescriptorIndex implements ToolDescriptorIndex {

    private final Map<String, ToolDescriptor> byCode;
    private final Set<String> allowFilter;

    public InMemoryToolDescriptorIndex() {
        this(new ConcurrentHashMap<>(), null);
    }

    private InMemoryToolDescriptorIndex(Map<String, ToolDescriptor> byCode, Set<String> allowFilter) {
        this.byCode = byCode;
        this.allowFilter = allowFilter;
    }

    /** 注册一条工具描述。 */
    @Override
    public void register(ToolDescriptor descriptor) {
        if (descriptor == null || descriptor.toolCode() == null || descriptor.toolCode().isBlank()) {
            return;
        }
        byCode.put(descriptor.toolCode(), descriptor);
    }

    /** 按编码移除工具描述。 */
    @Override
    public void unregister(String toolCode) {
        if (toolCode != null) {
            byCode.remove(toolCode);
        }
    }

    /** 整体替换工具描述集合。 */
    @Override
    public void replaceAll(Collection<ToolDescriptor> descriptors) {
        byCode.clear();
        if (descriptors == null) {
            return;
        }
        for (ToolDescriptor d : descriptors) {
            register(d);
        }
    }

    /** get。 */
    @Override
    public Optional<ToolDescriptor> get(String toolCode) {
        if (toolCode == null || !visible(toolCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(toolCode));
    }

    /** 按允许集生成只读视图。 */
    @Override
    public ToolDescriptorIndex view(Collection<String> allowToolCodes) {
        Set<String> allow = allowToolCodes == null
                ? Set.of()
                : allowToolCodes.stream().filter(c -> c != null && !c.isBlank()).collect(Collectors.toUnmodifiableSet());
        return new InMemoryToolDescriptorIndex(byCode, allow);
    }

    /** 按关键词搜索工具。 */
    @Override
    public List<ToolSearchHit> search(ToolSearchQuery query) {
        if (query == null) {
            return List.of();
        }
        Set<String> allow = query.allowToolCodes();
        if (allow == null || allow.isEmpty()) {
            return List.of();
        }
        String text = query.text() == null ? "" : query.text().trim().toLowerCase(Locale.ROOT);
        return byCode.values().stream()
                .filter(d -> allow.contains(d.toolCode()))
                .filter(d -> allowFilter == null || allowFilter.contains(d.toolCode()))
                .filter(d -> text.isEmpty() || matches(d, text))
                .limit(query.topK())
                .map(d -> new ToolSearchHit(d.toolCode(), d.name(), d.summary(), 1.0))
                .toList();
    }

    /** 工具编码是否在当前允许集内。 */
    private boolean visible(String toolCode) {
        return allowFilter == null || allowFilter.contains(toolCode);
    }

    /** 判断给定码是否等于当前枚举值。 */
    private static boolean matches(ToolDescriptor d, String text) {
        return contains(d.toolCode(), text)
                || contains(d.name(), text)
                || contains(d.summary(), text)
                || (d.tags() != null && d.tags().stream().anyMatch(t -> contains(t, text)));
    }

    /** 忽略大小写判断包含。 */
    private static boolean contains(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needle);
    }
}
