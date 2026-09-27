package org.deepstack.ai.chat.model.dto.structured.response;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 通用结构化生成结果。
 * <p>
 * 调用方按业务自行把 {@link #data} 反序列化为具体 DTO。
 * </p>
 *
 */
@Data
public class StructuredGenerateResult implements Serializable {

    /** 实际使用的智能体编码。 */
    private String agentCode;

    /** 智能体绑定的模型 ID（字符串形式，便于跨服务日志关联）。 */
    private String modelCode;

    /**
     * 模型输出的 JSON 对象（已解析为 Map）。
     */
    private Map<String, Object> data;
}
