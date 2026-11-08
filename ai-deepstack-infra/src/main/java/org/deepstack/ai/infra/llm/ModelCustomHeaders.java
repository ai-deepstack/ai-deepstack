package org.deepstack.ai.infra.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;

import java.util.Map;

/**
 * 解析 {@code ai_model.custom_headers}（JSON 对象）为请求头 Map。
 */
final class ModelCustomHeaders {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {
    };

    private ModelCustomHeaders() {
    }

    /** 无配置时返回空 Map；JSON 非法时抛业务异常，避免静默丢头。 */
    static Map<String, String> parse(AiModel config) {
        String json = config.getCustomHeaders();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> headers = MAPPER.readValue(json, TYPE);
            return headers == null || headers.isEmpty() ? Map.of() : headers;
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "模型 custom_headers 不是合法的 JSON 对象: modelCode=" + config.getModelCode());
        }
    }
}
