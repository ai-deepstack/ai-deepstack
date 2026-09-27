package org.deepstack.ai.tool.mcp;

import org.deepstack.ai.tool.mcp.model.entity.AiMcpConnection;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 {@code connection_code} 持有 / 重建 {@link McpSyncClient}。
 * <p>
 * 连接来自库表 {@code ai_mcp_connection}，不读 Spring AI yml 自动配置。
 * 配置变更或停用时应 {@link #remove(String)}，避免脏 Client。
 */
@Slf4j
@Component
public class McpClientRegistry {

    /** connectionCode → 已 initialize 的同步 Client */
    private final ConcurrentHashMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    /**
     * 获取已初始化的 Client。
     *
     * @param connectionCode 连接编码
     * @return 已初始化的 Client；未连接过或编码空白则 {@code null}（调用方应先 sync / put）
     */
    public McpSyncClient get(String connectionCode) {
        if (!StringUtils.hasText(connectionCode)) {
            return null;
        }
        String code = connectionCode.trim();
        McpSyncClient client = clients.get(code);
        if (client == null) {
            log.debug("McpClientRegistry: get miss code={}", code);
        }
        return client;
    }

    /**
     * 关闭旧连接（若有）后按配置重建并 initialize。
     *
     * @param conn 连接实体
     * @return 新的已初始化 Client
     * @throws IllegalArgumentException 编码为空或不支持的 transport
     * @throws RuntimeException         建连或 initialize 失败
     */
    public synchronized McpSyncClient put(AiMcpConnection conn) {
        if (conn == null || !StringUtils.hasText(conn.getConnectionCode())) {
            throw new IllegalArgumentException("connectionCode 不能为空");
        }
        String code = conn.getConnectionCode().trim();
        Integer transport = conn.getTransport();
        log.info("McpClientRegistry: connecting code={}, transport={}, endpoint={}, command={}, timeoutMs={}",
                code, transport, conn.getEndpoint(), conn.getCommand(), conn.getRequestTimeoutMs());
        // 先关掉同编码旧 Client，再重建
        closeQuietly(code);
        try {
            McpSyncClient client = buildAndInit(conn);
            clients.put(code, client);
            log.info("McpClientRegistry: connected ok code={}, transport={}, liveClients={}",
                    code, transport, clients.size());
            return client;
        } catch (Exception e) {
            log.error("McpClientRegistry: connect failed code={}, transport={}: {}",
                    code, transport, e.getMessage(), e);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 从注册表移除并关闭指定连接的 Client。
     *
     * @param connectionCode 连接编码；空白则忽略
     */
    public synchronized void remove(String connectionCode) {
        if (StringUtils.hasText(connectionCode)) {
            String code = connectionCode.trim();
            log.info("McpClientRegistry: remove code={}", code);
            closeQuietly(code);
        }
    }

    /**
     * 关闭并清空全部内存 Client（应用关闭或全量重置时使用）。
     */
    public void closeAll() {
        log.info("McpClientRegistry: closeAll size={}", clients.size());
        for (String code : new ArrayList<>(clients.keySet())) {
            closeQuietly(code);
        }
    }

    /**
     * 当前内存中的连接数（排查用）。
     *
     * @return 存活 Client 数量
     */
    public int size() {
        return clients.size();
    }

    /**
     * 安静关闭并移除指定编码的 Client；关闭异常只记 warn。
     *
     * @param code 连接编码
     */
    private void closeQuietly(String code) {
        McpSyncClient old = clients.remove(code);
        if (old == null) {
            return;
        }
        try {
            old.close();
            log.debug("McpClientRegistry: closed previous client code={}", code);
        } catch (Exception e) {
            log.warn("McpClientRegistry: close failed code={}: {}", code, e.getMessage());
        }
    }

    /**
     * 按 transport 构建 Client 并完成 initialize。
     *
     * @param conn 连接实体
     * @return 已 initialize 的 Client
     * @throws IllegalArgumentException 不支持的 transport
     */
    private McpSyncClient buildAndInit(AiMcpConnection conn) {
        org.deepstack.ai.kernel.enums.tool.McpTransportEnum transport =
                org.deepstack.ai.kernel.enums.tool.McpTransportEnum.ofRequired(conn.getTransport());
        Duration timeout = Duration.ofMillis(
                conn.getRequestTimeoutMs() != null && conn.getRequestTimeoutMs() > 0
                        ? conn.getRequestTimeoutMs()
                        : 30_000);
        return switch (transport) {
            case STDIO -> buildStdio(conn, timeout);
            case SSE -> buildSse(conn, timeout);
        };
    }

    /**
     * 以 STDIO 方式启动子进程 MCP Server 并 initialize。
     * <p>
     * Windows 下通过 {@code cmd.exe /c} 包装，避免 shell 脚本无法直接执行。
     *
     * @param conn    连接实体（需含 command）
     * @param timeout 请求超时
     * @return 已 initialize 的 Client
     * @throws IllegalArgumentException command 未配置
     */
    private McpSyncClient buildStdio(AiMcpConnection conn, Duration timeout) {
        if (!StringUtils.hasText(conn.getCommand())) {
            throw new IllegalArgumentException("STDIO 连接必须配置 command");
        }
        List<String> args = parseArgs(conn.getArgsJson());
        String command = conn.getCommand().trim();
        String os = System.getProperty("os.name", "").toLowerCase();
        ServerParameters params;
        if (os.contains("win")) {
            // Windows：cmd.exe /c <command> <args...>
            List<String> winArgs = new ArrayList<>();
            winArgs.add("/c");
            winArgs.add(command);
            winArgs.addAll(args);
            params = ServerParameters.builder("cmd.exe").args(winArgs).build();
            log.debug("McpClientRegistry: STDIO via cmd.exe code={}, argsCount={}",
                    conn.getConnectionCode(), winArgs.size());
        } else {
            params = ServerParameters.builder(command).args(args).build();
            log.debug("McpClientRegistry: STDIO direct code={}, argsCount={}",
                    conn.getConnectionCode(), args.size());
        }
        var transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(timeout)
                .clientInfo(new McpSchema.Implementation("ai-deepstack", "0.1.0"))
                .build();
        client.initialize();
        return client;
    }

    /**
     * 以 SSE/HTTP 方式连接远端 MCP Server 并 initialize。
     *
     * @param conn    连接实体（需含 endpoint）
     * @param timeout 请求超时
     * @return 已 initialize 的 Client
     * @throws IllegalArgumentException endpoint 未配置
     */
    private McpSyncClient buildSse(AiMcpConnection conn, Duration timeout) {
        if (!StringUtils.hasText(conn.getEndpoint())) {
            throw new IllegalArgumentException("SSE/HTTP 连接必须配置 endpoint");
        }
        Map<String, String> headers = parseHeaders(conn.getHeadersJson(), conn.getSecret());
        log.debug("McpClientRegistry: SSE/HTTP code={}, endpoint={}, extraHeaderKeys={}, hasAuth={}",
                conn.getConnectionCode(),
                conn.getEndpoint(),
                headers.keySet().stream().filter(k -> !"Authorization".equalsIgnoreCase(k)).toList(),
                headers.containsKey("Authorization"));
        var builder = HttpClientSseClientTransport.builder(conn.getEndpoint().trim());
        if (!headers.isEmpty()) {
            // 将合并后的头注入每次 HTTP 请求
            builder.httpRequestCustomizer((reqBuilder, method, endpoint, body, context) ->
                    headers.forEach(reqBuilder::header));
        }
        McpSyncClient client = McpClient.sync(builder.build())
                .requestTimeout(timeout)
                .clientInfo(new McpSchema.Implementation("ai-deepstack", "0.1.0"))
                .build();
        client.initialize();
        return client;
    }

    /**
     * 解析 {@code args_json} 为字符串参数列表。
     *
     * @param argsJson JSON 数组字符串
     * @return 参数列表；空白或非法空数组时返回空列表
     */
    private static List<String> parseArgs(String argsJson) {
        if (!StringUtils.hasText(argsJson)) {
            return List.of();
        }
        JSONArray arr = JSON.parseArray(argsJson.trim());
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            Object v = arr.get(i);
            if (v != null) {
                out.add(String.valueOf(v));
            }
        }
        return out;
    }

    /**
     * 合并 headers_json 与 secret；secret 仅写入 Authorization（已有则不覆盖）。
     * <p>
     * 日志侧禁止打印 secret 明文。
     *
     * @param headersJson 额外请求头 JSON 对象
     * @param secret      鉴权密钥
     * @return 合并后的请求头 Map
     */
    private static Map<String, String> parseHeaders(String headersJson, String secret) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(headersJson)) {
            JSONObject obj = JSON.parseObject(headersJson.trim());
            if (obj != null) {
                for (String key : obj.keySet()) {
                    Object v = obj.get(key);
                    if (v != null) {
                        headers.put(key, String.valueOf(v));
                    }
                }
            }
        }
        // secret 未带 Bearer 时自动补前缀；headers 已有 Authorization 则不覆盖
        if (StringUtils.hasText(secret) && !headers.containsKey("Authorization")) {
            String s = secret.trim();
            headers.put("Authorization",
                    s.regionMatches(true, 0, "Bearer ", 0, 7) ? s : "Bearer " + s);
        }
        return headers;
    }
}
