package org.deepstack.ai.tool.mcp.model.dto.request;

import lombok.Data;

/**
 * 创建 MCP 连接的请求体。
 */
@Data
public class McpConnectionCreateRequest {

    /** 连接编码（必填，全局唯一） */
    private String connectionCode;

    /** 连接显示名称；为空时默认用 connectionCode */
    private String connectionName;

    /** 传输类型码：见 McpTransportEnum */
    private Integer transport;

    /** HTTP/SSE 端点 URL（SSE、STREAMABLE_HTTP 必填） */
    private String endpoint;

    /** STDIO 启动命令（STDIO 必填） */
    private String command;

    /** STDIO 参数 JSON 数组，如 {@code ["-y","@modelcontextprotocol/server-filesystem","/data"]} */
    private String argsJson;

    /** 鉴权密钥（可选；会写入 Authorization，已有 Bearer 前缀则原样使用） */
    private String secret;

    /** 额外 HTTP 头，JSON 对象字符串 */
    private String headersJson;

    /** 请求超时毫秒；为空时默认 30000 */
    private Integer requestTimeoutMs;

    /** 是否启用：1=启用，0=停用；为空时默认启用 */
    private Integer enabled;
}
