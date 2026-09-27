package org.deepstack.ai.chat.web.structured;

import org.deepstack.ai.chat.model.dto.structured.request.StructuredGenerateRequest;
import org.deepstack.ai.chat.model.dto.structured.response.StructuredGenerateResult;
import org.deepstack.ai.chat.service.structured.StructuredGenerateService;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用结构化生成 API。
 * <p>
 * 传 {@code agentCode} + {@code input}，由智能体配置决定模型与提示词，
 * 无需为每个智能体单独建 Controller。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/structured")
@RequiredArgsConstructor
public class StructuredGenerateController {

    private final StructuredGenerateService structuredGenerateService;

    /**
     * 非流式结构化生成。
     * <p>
     * 按智能体编码解析模型与提示词，将输入转为结构化 JSON。
     * </p>
     *
     * @param req 智能体编码 {@code agentCode} + 输入 {@code input}
     * @return JSON 对象结果（含 agentCode、modelCode、data）
     */
    @PostMapping("/generate")
    public Response<StructuredGenerateResult> generate(@RequestBody StructuredGenerateRequest req) {
        long startMs = System.currentTimeMillis();
        String agentCode = req != null ? req.getAgentCode() : null;
        log.info("结构化生成 api 入口: agentCode={}, inputNull={}",
                agentCode, req == null || req.getInput() == null);
        try {
            StructuredGenerateResult result = structuredGenerateService.generate(req);
            log.info("结构化生成 api 成功: agentCode={}, modelCode={}, dataKeys={}, 耗时毫秒={}",
                    result != null ? result.getAgentCode() : agentCode,
                    result != null ? result.getModelCode() : null,
                    result != null && result.getData() != null ? result.getData().keySet() : null,
                    System.currentTimeMillis() - startMs);
            return Response.success(result);
        } catch (Exception ex) {
            log.error("结构化生成 api 失败: agentCode={}, 耗时毫秒={}, 错误={}",
                    agentCode, System.currentTimeMillis() - startMs, ex.getMessage(), ex);
            throw ex;
        }
    }
}
