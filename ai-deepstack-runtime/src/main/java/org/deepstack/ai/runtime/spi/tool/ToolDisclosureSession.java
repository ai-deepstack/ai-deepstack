package org.deepstack.ai.runtime.spi.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;

/**
 * 渐进披露会话：元工具 + 已 load 集合。
 * <p>
 * 允许集在 {@link ToolDisclosureSessionFactory#open} 时固定；越权 code 在 load 时丢弃。
 * </p>
 */
public interface ToolDisclosureSession {

    /** 元工具名约定（本期可不注册 @Tool Bean）。 */
    String META_TOOL_SEARCH = "tool_search";
    String META_LOAD_TOOLS = "load_tools";

    /**
     * 始终可用的元工具回调（tool_search / load_tools）。Noop 可返回空。
     */
    List<ToolCallback> metaToolCallbacks();

    List<ToolSearchHit> search(String query, int topK);

    /**
     * codes 必须 ⊆ 会话允许集；越权丢弃。load 后描述可含完整 schema。
     */
    List<ToolDescriptor> load(List<String> toolCodes);

    /**
     * 本轮交给模型的可执行集。
     * <p>
     * disclosureMode=OFF（Noop）时：对允许集全量 resolve，行为与历史一致。
     * </p>
     */
    List<ToolCallback> exposedToolCallbacks();

    Set<String> loadedToolCodes();
}
