package org.deepstack.ai.chat.model.dto.structured.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用结构化生成请求：按智能体配置调模型，强制返回 JSON 对象。
 *
 */
@Data
public class StructuredGenerateRequest implements Serializable {

    /**
     * 智能体编码；决定模型、温度、systemPrompt、responseFormat。
     */
    private String agentCode;

    /**
     * 结构化输入（任意 JSON 可序列化对象），会进入 user 消息。
     */
    private Object input;

    /**
     * 可选：user 消息前缀；空则用默认「请根据以下输入生成结构化 JSON 结果：」。
     */
    private String userMessage;
}
