package org.deepstack.ai.tool.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 远端工具 → Spring AI {@link ToolCallback}。
 * <p>
 * 对外工具名使用平台 {@code toolCode}。listTools 结果按连接缓存，避免每次建回调都打远端。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolBridge {

    private final McpClientRegistry clientRegistry;

    /** 缓存键：connectionCode + remoteName + platformToolCode */
    private final ConcurrentHashMap<String, ToolCallback> cache = new ConcurrentHashMap<>();

    /** connectionCode → 远端工具列表缓存 */
    private final ConcurrentHashMap<String, List<McpSchema.Tool>> listToolsCache = new ConcurrentHashMap<>();

    /**
     * 解析 MCP 远端工具为 Spring AI {@link ToolCallback}（带缓存）。
     *
     * @param connectionCode    MCP 连接编码
     * @param remoteToolName    远端工具原名
     * @param platformToolCode  平台 toolCode（对外暴露名）
     * @return ToolCallback；参数空白时返回 null
     */
    public ToolCallback resolve(String connectionCode, String remoteToolName, String platformToolCode) {
        log.info("McpToolBridge resolve: connection={}, remote={}, toolCode={}",
                connectionCode, remoteToolName, platformToolCode);
        if (!StringUtils.hasText(connectionCode) || !StringUtils.hasText(remoteToolName)) {
            log.warn("McpToolBridge: resolve skipped, blank connection or remoteName");
            return null;
        }
        String code = connectionCode.trim();
        String remote = remoteToolName.trim();
        String toolCode = StringUtils.hasText(platformToolCode) ? platformToolCode.trim() : remote;
        String key = code + "\0" + remote + "\0" + toolCode;
        boolean cached = cache.containsKey(key);
        ToolCallback cb = cache.computeIfAbsent(key, k -> build(code, remote, toolCode));
        if (cached) {
            log.debug("McpToolBridge: cache hit connection={}, remote={}, toolCode={}", code, remote, toolCode);
        }
        return cb;
    }

    /** 清空全部 ToolCallback 与 listTools 缓存。 */
    public void evictAll() {
        int n = cache.size();
        int lists = listToolsCache.size();
        cache.clear();
        listToolsCache.clear();
        log.info("McpToolBridge: evictAll callbacks={}, listTools={}", n, lists);
    }

    /**
     * 驱逐指定连接下的回调与 listTools 缓存。
     *
     * @param connectionCode MCP 连接编码
     */
    public void evictConnection(String connectionCode) {
        if (!StringUtils.hasText(connectionCode)) {
            return;
        }
        String code = connectionCode.trim();
        String prefix = code + "\0";
        int before = cache.size();
        cache.keySet().removeIf(k -> k.startsWith(prefix));
        listToolsCache.remove(code);
        log.info("McpToolBridge: evictConnection code={}, removedCallbacks≈{}",
                code, Math.max(0, before - cache.size()));
    }

    /** 组装 MCP 工具回调。 */
    private ToolCallback build(String connectionCode, String remoteToolName, String platformToolCode) {
        log.debug("McpToolBridge: build callback connection={}, remote={}, toolCode={}",
                connectionCode, remoteToolName, platformToolCode);
        McpSyncClient client = clientRegistry.get(connectionCode);
        if (client == null) {
            log.error("McpToolBridge: client not ready connection={}", connectionCode);
            throw new IllegalStateException("MCP 连接未就绪: " + connectionCode);
        }
        McpSchema.Tool tool = findTool(connectionCode, client, remoteToolName);
        if (tool == null) {
            log.error("McpToolBridge: remote tool missing connection={}, remote={}",
                    connectionCode, remoteToolName);
            throw new IllegalStateException(
                    "MCP 工具不存在: connection=" + connectionCode + ", name=" + remoteToolName);
        }
        ToolCallback cb = SyncMcpToolCallback.builder()
                .mcpClient(client)
                .tool(tool)
                .prefixedToolName(platformToolCode)
                .build();
        log.info("McpToolBridge: built ToolCallback connection={}, remote={}, toolCode={}",
                connectionCode, remoteToolName, platformToolCode);
        return cb;
    }

    /** findTool。 */
    private McpSchema.Tool findTool(String connectionCode, McpSyncClient client, String remoteToolName) {
        List<McpSchema.Tool> tools = cachedListTools(connectionCode, client);
        for (McpSchema.Tool t : tools) {
            if (remoteToolName.equals(t.name())) {
                return t;
            }
        }
        // 缓存可能过期：清掉再拉一次
        listToolsCache.remove(connectionCode);
        tools = cachedListTools(connectionCode, client);
        for (McpSchema.Tool t : tools) {
            if (remoteToolName.equals(t.name())) {
                return t;
            }
        }
        return null;
    }

    /** cachedListTools。 */
    private List<McpSchema.Tool> cachedListTools(String connectionCode, McpSyncClient client) {
        return listToolsCache.computeIfAbsent(connectionCode, code -> {
            McpSchema.ListToolsResult result = client.listTools();
            List<McpSchema.Tool> tools = result != null && result.tools() != null
                    ? List.copyOf(result.tools()) : List.of();
            log.info("McpToolBridge: listTools cached connection={}, count={}", code, tools.size());
            return tools;
        });
    }

    /**
     * 强制刷新并返回远端工具列表（sync 路径使用）。
     *
     * @param connectionCode MCP 连接编码
     * @return 远端工具元数据列表
     * @throws IllegalStateException Client 未就绪
     */
    public List<McpSchema.Tool> listRemoteTools(String connectionCode) {
        log.info("McpToolBridge listRemoteTools: connection={}", connectionCode);
        McpSyncClient client = clientRegistry.get(connectionCode);
        if (client == null) {
            log.error("McpToolBridge: listRemoteTools client missing connection={}", connectionCode);
            throw new IllegalStateException("MCP 连接未就绪: " + connectionCode);
        }
        // sync 路径强制刷新
        listToolsCache.remove(connectionCode.trim());
        List<McpSchema.Tool> tools = cachedListTools(connectionCode.trim(), client);
        if (log.isDebugEnabled()) {
            for (McpSchema.Tool t : tools) {
                log.debug("McpToolBridge: remote tool name={}, title={}", t.name(), t.title());
            }
        }
        return tools;
    }
}
