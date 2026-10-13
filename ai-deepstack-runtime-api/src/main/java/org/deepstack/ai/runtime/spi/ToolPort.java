package org.deepstack.ai.runtime.spi;

import org.deepstack.ai.engine.tool.AgentTool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 工具能力端口：业务键为 toolCode。机械解析，不感知 Agent 绑定与渐进披露。
 */
public interface ToolPort {

    List<ToolCallback> resolveToolCallbacksByCodes(List<String> toolCodes);

    /**
     * 按单个 toolCode 解析（卡片 regenerate 等）。
     */
    default List<ToolCallback> resolveToolCallbacksByCode(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return List.of();
        }
        return resolveToolCallbacksByCodes(List.of(toolCode));
    }

    AgentTool getAgentTool(String toolCode);

    List<AgentTool> listAgentTools();

    /** 失效指定 toolCode 的解析缓存（及依赖该缓存的聚合键）。 */
    default void evict(String toolCode) {
        // no-op
    }

    /** 清空全部解析缓存。 */
    default void evictAll() {
        // no-op
    }
}
