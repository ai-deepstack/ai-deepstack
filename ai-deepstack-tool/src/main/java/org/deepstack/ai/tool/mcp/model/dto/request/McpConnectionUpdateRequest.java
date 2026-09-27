package org.deepstack.ai.tool.mcp.model.dto.request;

import lombok.Data;

/**
 * 更新 MCP 连接配置的请求体（不含密钥；密钥走独立接口）。
 * <p>
 * 除 {@code id} 外均为可选；传入非 null 字段才会覆盖。
 */
@Data
public class McpConnectionUpdateRequest {

    /** 连接主键（必填） */
    private Long id;

    /** 连接显示名称 */
    private String connectionName;

    /** 传输类型码：见 McpTransportEnum */
    private Integer transport;

    /** HTTP/SSE 端点 URL */
    private String endpoint;

    /** STDIO 启动命令 */
    private String command;

    /** STDIO 参数 JSON 数组字符串 */
    private String argsJson;

    /** 额外 HTTP 头 JSON 对象字符串 */
    private String headersJson;

    /** 请求超时毫秒 */
    private Integer requestTimeoutMs;

    /** 是否启用：1=启用，0=停用 */
    private Integer enabled;
}
