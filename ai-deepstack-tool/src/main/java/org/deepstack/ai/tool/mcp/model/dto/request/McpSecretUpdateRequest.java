package org.deepstack.ai.tool.mcp.model.dto.request;

import lombok.Data;

/**
 * 单独更新 MCP 连接密钥的请求体。
 */
@Data
public class McpSecretUpdateRequest {

    /** 新密钥；空白或 null 表示清空已配置密钥 */
    private String secret;
}
