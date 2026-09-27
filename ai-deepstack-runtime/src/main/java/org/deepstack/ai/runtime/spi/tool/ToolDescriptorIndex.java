package org.deepstack.ai.runtime.spi.tool;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 真 Registry：进程内工具描述索引（不碰 DB、不执行工具）。
 * <p>
 * search 必须通过 {@link #view(Collection)} 得到的允许集视图，禁止对全平台裸搜。
 * </p>
 */
public interface ToolDescriptorIndex {

    void register(ToolDescriptor descriptor);

    void unregister(String toolCode);

    /** Catalog hydrate：整体替换。 */
    void replaceAll(Collection<ToolDescriptor> descriptors);

    Optional<ToolDescriptor> get(String toolCode);

    /**
     * Agent/会话视图：仅允许集内可见。
     */
    ToolDescriptorIndex view(Collection<String> allowToolCodes);

    List<ToolSearchHit> search(ToolSearchQuery query);
}
