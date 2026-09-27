package org.deepstack.ai.tool.mcp.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 连接详情/列表项响应（不含密钥明文）。
 */
@Data
public class McpConnectionResponse {

    /** 连接主键 */
    private Long id;

    /** 连接编码 */
    private String connectionCode;

    /** 连接显示名称 */
    private String connectionName;

    /** 传输类型码：见 McpTransportEnum */
    private Integer transport;

    /** 传输类型中文名 */
    private String transportName;

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

    /** 启用状态中文名 */
    private String enabledName;

    /** 最近一次建连/同步失败原因 */
    private String lastError;

    /** 是否已配置密钥（不回传明文） */
    private boolean secretConfigured;

    /** 该连接下当前启用的 MCP 工具数量 */
    private Integer toolCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
