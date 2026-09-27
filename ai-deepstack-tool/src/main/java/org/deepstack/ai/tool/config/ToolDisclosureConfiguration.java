package org.deepstack.ai.tool.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 工具披露与 MCP 相关配置属性注册。
 */
@Configuration
@EnableConfigurationProperties({DeepstackToolProperties.class, DeepstackMcpProperties.class})
public class ToolDisclosureConfiguration {
}
