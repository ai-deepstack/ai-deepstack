package org.deepstack.ai.tool.spi;

import org.deepstack.ai.engine.tool.AgentTool;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.tool.resolve.CatalogToolCallbackResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link ToolPort} 适配：按 toolCode 解析回调；平台内建 {@link AgentTool} 列表来自 Spring 容器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPortAdapter implements ToolPort {

    private final CatalogToolCallbackResolver toolCallbackResolver;
    private final List<AgentTool> agentTools;

    /**
     * 按多个 toolCode 解析 Spring AI {@link ToolCallback}。
     *
     * @param toolCodes 工具编码列表
     * @return 回调列表
     */
    @Override
    public List<ToolCallback> resolveToolCallbacksByCodes(List<String> toolCodes) {
        List<ToolCallback> callbacks = toolCallbackResolver.resolveByCodes(toolCodes);
        log.debug("ToolPortAdapter: resolveByCodes requested={}, resolved={}",
                toolCodes == null ? 0 : toolCodes.size(),
                callbacks.size());
        return callbacks;
    }

    /**
     * 按单个 toolCode 解析 {@link ToolCallback}。
     *
     * @param toolCode 工具编码
     * @return 回调列表
     */
    @Override
    public List<ToolCallback> resolveToolCallbacksByCode(String toolCode) {
        log.info("ToolPortAdapter: resolveByCode toolCode={}", toolCode);
        return toolCallbackResolver.resolveByCode(toolCode);
    }

    /**
     * 按编码查找平台内建 {@link AgentTool}。
     *
     * @param toolCode 工具编码
     * @return 匹配的 AgentTool；未找到或入参非法返回 {@code null}
     */
    @Override
    public AgentTool getAgentTool(String toolCode) {
        if (toolCode == null || agentTools == null) {
            return null;
        }
        return agentTools.stream()
                .filter(t -> toolCode.equals(t.getCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 列出容器中全部平台内建 {@link AgentTool}。
     *
     * @return AgentTool 列表；未注入时返回空列表
     */
    @Override
    public List<AgentTool> listAgentTools() {
        return agentTools != null ? agentTools : List.of();
    }

    /**
     * 驱逐指定 toolCode 的回调缓存。
     *
     * @param toolCode 工具编码
     */
    @Override
    public void evict(String toolCode) {
        log.info("ToolPortAdapter: evict toolCode={}", toolCode);
        toolCallbackResolver.evict(toolCode);
    }

    /**
     * 清空全部工具回调缓存。
     */
    @Override
    public void evictAll() {
        log.info("ToolPortAdapter: evictAll");
        toolCallbackResolver.evictAll();
    }
}
