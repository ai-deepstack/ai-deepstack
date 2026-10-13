package org.deepstack.ai.chat.service.structured;

import org.deepstack.ai.chat.model.dto.structured.request.StructuredGenerateRequest;
import org.deepstack.ai.chat.model.dto.structured.response.StructuredGenerateResult;

/**
 * 通用结构化生成：按 Agent 配置非流式产出 JSON。
 *
 */
public interface StructuredGenerateService {

    /**
     * 按 Agent 调用模型并解析 JSON 对象。
     *
     * @param request Agent 编码 + 输入
     * @return 结构化结果；失败抛业务异常
     */
    StructuredGenerateResult generate(StructuredGenerateRequest request);
}
