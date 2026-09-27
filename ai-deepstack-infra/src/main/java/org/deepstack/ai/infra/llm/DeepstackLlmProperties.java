package org.deepstack.ai.infra.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM HTTP 客户端超时（CHAT / EMBEDDING 共用）。
 */
@Data
@ConfigurationProperties(prefix = "deepstack.llm")
public class DeepstackLlmProperties {

    /** TCP 连接超时毫秒 */
    private int connectTimeoutMs = 5_000;

    /** 读超时毫秒（非流式整包；流式场景由 SDK 按请求处理） */
    private int readTimeoutMs = 60_000;
}
