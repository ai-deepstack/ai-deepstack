package org.deepstack.ai.tool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 能力配置（前缀 {@code deepstack.mcp}）。
 * <p>
 * 连接清单在库表，不在 yml；此处仅总开关与 toolCode 前缀。
 */
@Data
@ConfigurationProperties(prefix = "deepstack.mcp")
public class DeepstackMcpProperties {

    /**
     * yml 兜底默认；运行时总开关以 sys_config {@code mcp.enabled}（0/1）为准。
     */
    private boolean enabled = true;

    /**
     * yml 兜底；运行时以 sys_config {@code mcp.tool-code-prefix} 为准。
     */
    private String toolCodePrefix = "mcp_";
}
