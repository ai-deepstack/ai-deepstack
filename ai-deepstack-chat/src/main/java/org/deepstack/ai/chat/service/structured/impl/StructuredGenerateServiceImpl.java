package org.deepstack.ai.chat.service.structured.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import org.deepstack.ai.chat.model.dto.structured.request.StructuredGenerateRequest;
import org.deepstack.ai.chat.model.dto.structured.response.StructuredGenerateResult;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.infra.llm.AiChatClientFactory;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.agent.service.AiAgentService;
import org.deepstack.ai.chat.service.structured.StructuredGenerateService;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 通用结构化生成实现。
 * <p>
 * 流程：解析智能体 → 取模型 → 拼 system/user → 强制 JSON_OBJECT 调模型 → 解析为 Map 返回。
 * 本类不做业务字段校验（如运动白名单），由调用方或场景 Prompt/JSON Schema 约束。
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredGenerateServiceImpl implements StructuredGenerateService {

    /** user 消息默认前缀（调用方未传 userMessage 时使用）。 */
    private static final String DEFAULT_USER_PREFIX =
            "请根据以下输入生成结构化 JSON 结果（只输出 JSON 对象，不要 Markdown）：";

    private final AiAgentService aiAgentService;
    private final AiModelService aiModelService;
    private final AiChatClientFactory aiChatClientFactory;

    /**
     * 按智能体配置调用大模型，返回解析后的 JSON 对象。
     * <p>日志记录 agentCode / modelId / 耗时与预览，不输出密钥。</p>
     *
     * @param request 须含 agentCode；input 会序列化进 user 消息
     * @return 结构化结果
     */
    @Override
    public StructuredGenerateResult generate(StructuredGenerateRequest request) {
        if (request == null || !StringUtils.hasText(request.getAgentCode())) {
            log.warn("结构化生成参数无效: request 为空或 agentCode 缺失");
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "agentCode 不能为空");
        }
        String agentCode = request.getAgentCode().trim();
        long startMs = System.currentTimeMillis();
        log.info("结构化生成收到请求: agentCode={}, 有自定义userMessage={}, input类型={}",
                agentCode,
                StringUtils.hasText(request.getUserMessage()),
                request.getInput() == null ? "null" : request.getInput().getClass().getSimpleName());

        // 1) 场景与模型
        AiAgent scene = aiAgentService.getByAgentCode(agentCode);
        if (scene == null || !StringUtils.hasText(scene.getModelCode())) {
            log.error("结构化生成场景不可用: agentCode={}", agentCode);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(), "未找到可用智能体: " + agentCode);
        }
        AiModel model = aiModelService.getByModelCode(scene.getModelCode());
        if (model == null) {
            log.error("结构化生成模型不可用: agentCode={}, modelCode={}", agentCode, scene.getModelCode());
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "模型不存在或已禁用: modelCode=" + scene.getModelCode());
        }

        String modelCode = model.getModelCode();
        OpenAiChatModel chatModel = aiChatClientFactory.getChatModel(modelCode);
        String apiModelName = aiChatClientFactory.getApiModelName(modelCode);
        Double temperature = scene.getTemperature() != null ? scene.getTemperature().doubleValue() : 0.4;
        Integer maxTokens = scene.getMaxTokens() != null ? scene.getMaxTokens() : 1200;
        log.info("结构化生成场景解析完成: agentCode={}, agentId={}, modelCode={}, apiModelName={}, "
                        + "responseFormat={}, temperature={}, maxTokens={}, topP={}, systemPromptLen={}",
                agentCode, scene.getId(), modelCode, apiModelName,
                scene.getResponseFormat(), temperature, maxTokens, scene.getTopP(),
                scene.getSystemPrompt() != null ? scene.getSystemPrompt().length() : 0);

        // 2) 调用参数（强制 JSON 对象，便于下游解析）
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(apiModelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                        .build());
        if (scene.getTopP() != null) {
            optionsBuilder.topP(scene.getTopP().doubleValue());
        }

        // 3) Prompt
        String systemPrompt = buildSystemPrompt(scene);
        String inputJson = request.getInput() == null ? "{}" : JSON.toJSONString(request.getInput());
        String prefix = StringUtils.hasText(request.getUserMessage())
                ? request.getUserMessage().trim()
                : DEFAULT_USER_PREFIX;
        String userMsg = prefix + "\n" + inputJson;
        log.info("结构化生成 Prompt 就绪: agentCode={}, systemLen={}, userPrefixLen={}, inputJsonLen={}, "
                        + "userTotalLen={}, inputPreview={}",
                agentCode, systemPrompt.length(), prefix.length(), inputJson.length(), userMsg.length(),
                preview(inputJson, 400));
        log.debug("结构化生成 systemPrompt 预览: agentCode={}, preview={}",
                agentCode, preview(systemPrompt, 500));

        // 4) 调模型
        long llmStart = System.currentTimeMillis();
        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(
                    List.of(new SystemMessage(systemPrompt), new UserMessage(userMsg)),
                    optionsBuilder.build()));
        } catch (Exception ex) {
            log.error("结构化生成调用模型失败: agentCode={}, modelCode={}, apiModelName={}, "
                            + "llm耗时毫秒={}, 总耗时毫秒={}, 错误={}",
                    agentCode, modelCode, apiModelName,
                    System.currentTimeMillis() - llmStart,
                    System.currentTimeMillis() - startMs,
                    ex.getMessage(), ex);
            throw ex;
        }
        long llmMs = System.currentTimeMillis() - llmStart;

        String text = response != null && response.getResult() != null
                && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText()
                : null;
        log.info("结构化生成模型已返回: agentCode={}, modelCode={}, llm耗时毫秒={}, 响应字符数={}, 响应预览={}",
                agentCode, modelCode, llmMs,
                text == null ? 0 : text.length(),
                preview(text, 500));
        if (!StringUtils.hasText(text)) {
            log.error("结构化生成模型返回空内容: agentCode={}, modelCode={}, llm耗时毫秒={}",
                    agentCode, modelCode, llmMs);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(), "模型返回空内容");
        }

        // 5) 解析 JSON
        Map<String, Object> data;
        try {
            JSONObject root = JSON.parseObject(text);
            if (root == null) {
                log.error("结构化生成 JSON 解析为空对象: agentCode={}, 原文预览={}",
                        agentCode, preview(text, 300));
                throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(), "模型返回空 JSON");
            }
            data = root.to(new TypeReference<Map<String, Object>>() {
            });
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("结构化生成 JSON 解析失败: agentCode={}, 错误={}, 原文预览={}",
                    agentCode, ex.getMessage(), preview(text, 300));
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(), "模型返回非 JSON");
        }

        StructuredGenerateResult result = new StructuredGenerateResult();
        result.setAgentCode(agentCode);
        result.setModelCode(modelCode);
        result.setData(data);
        log.info("结构化生成成功: agentCode={}, modelCode={}, dataKeys={}, llm耗时毫秒={}, 总耗时毫秒={}",
                agentCode, modelCode, data.keySet(), llmMs, System.currentTimeMillis() - startMs);
        return result;
    }

    /**
     * 组装 system：场景 systemPrompt +（可选）JSON_SCHEMA / JSON 输出说明。
     *
     * @param scene 场景
     * @return system 全文
     */
    private static String buildSystemPrompt(AiAgent scene) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(scene.getSystemPrompt())) {
            sb.append(scene.getSystemPrompt().trim());
            log.debug("结构化生成使用场景 systemPrompt: agentCode={}, len={}",
                    scene.getAgentCode(), scene.getSystemPrompt().length());
        } else {
            sb.append("你是结构化 JSON 生成器。只输出一个 JSON 对象，禁止 Markdown 与解释性前后缀。");
            log.warn("结构化生成场景未配置 systemPrompt，使用默认文案: agentCode={}", scene.getAgentCode());
        }
        Integer format = scene.getResponseFormat();
        if (format != null
                && format == org.deepstack.ai.kernel.enums.model.ResponseFormatEnum.JSON_SCHEMA.getCode()
                && StringUtils.hasText(scene.getResponseSchema())) {
            sb.append(System.lineSeparator()).append(System.lineSeparator())
                    .append("[输出格式]").append(System.lineSeparator())
                    .append("请严格按以下 JSON Schema 输出，不要输出任何额外文字：")
                    .append(System.lineSeparator())
                    .append(scene.getResponseSchema().trim());
            log.info("结构化生成附加 JSON_SCHEMA: agentCode={}, schemaLen={}",
                    scene.getAgentCode(), scene.getResponseSchema().length());
        } else if (format == null
                || format == org.deepstack.ai.kernel.enums.model.ResponseFormatEnum.JSON.getCode()) {
            sb.append(System.lineSeparator()).append(System.lineSeparator())
                    .append("[输出格式]").append(System.lineSeparator())
                    .append("请以合法的 JSON 对象输出，不要输出任何额外文字。");
            log.debug("结构化生成附加 JSON 输出约束: agentCode={}, responseFormat={}",
                    scene.getAgentCode(), format);
        } else {
            log.info("结构化生成未附加额外格式段: agentCode={}, responseFormat={}",
                    scene.getAgentCode(), format);
        }
        return sb.toString();
    }

    /**
     * 截断预览，避免日志过大。
     *
     * @param text 原文
     * @param max  最大字符
     * @return 预览
     */
    private static String preview(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.replace('\n', ' ').replace('\r', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
