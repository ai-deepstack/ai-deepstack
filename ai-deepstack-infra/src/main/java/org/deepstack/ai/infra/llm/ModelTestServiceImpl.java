package org.deepstack.ai.infra.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.aimodel.model.dto.response.ModelTestResult;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.aimodel.service.ModelTestService;
import org.deepstack.ai.kernel.enums.model.ModelTypeEnum;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 模型连通性测试：复用运行时工厂的构建参数，发一次最小请求验证 baseUrl / Key / 模型名 / 自定义头。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTestServiceImpl implements ModelTestService {

    private static final String PROBE_TEXT = "ping";
    private static final int REPLY_PREVIEW_LENGTH = 60;

    private final AiChatClientFactory chatClientFactory;
    private final EmbeddingClientFactory embeddingClientFactory;

    /** 按模型类型发起最小调用；异常统一转成 {@code ok=false} 的结果。 */
    @Override
    public ModelTestResult test(AiModel config) {
        long start = System.currentTimeMillis();
        try {
            ModelTestResult result = ModelTypeEnum.EMBEDDING.getCode() == config.getModelType()
                    ? testEmbedding(config)
                    : testChat(config);
            result.setOk(true);
            result.setLatencyMs(System.currentTimeMillis() - start);
            log.info("模型连通性测试成功: modelCode={}, modelType={}, latencyMs={}",
                    config.getModelCode(), config.getModelType(), result.getLatencyMs());
            return result;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String message = resolveError(e);
            log.warn("模型连通性测试失败: modelCode={}, modelType={}, latencyMs={}, err={}",
                    config.getModelCode(), config.getModelType(), latency, message);
            ModelTestResult result = new ModelTestResult();
            result.setOk(false);
            result.setLatencyMs(latency);
            result.setMessage(message);
            return result;
        }
    }

    /** CHAT：一次短对话，验证鉴权、模型名与返回内容。 */
    private ModelTestResult testChat(AiModel config) {
        OpenAiChatModel model = chatClientFactory.buildChatModel(config);
        ChatResponse response = model.call(new Prompt(List.of(new UserMessage(PROBE_TEXT))));
        String reply = response != null && response.getResult() != null
                && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText()
                : null;
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("模型返回空内容，请检查 apiModelName");
        }
        ModelTestResult result = new ModelTestResult();
        result.setMessage("调用成功，回复：" + truncate(reply.trim()));
        return result;
    }

    /** EMBEDDING：一次短文本向量化，验证鉴权并回显维度。 */
    private ModelTestResult testEmbedding(AiModel config) {
        OpenAiEmbeddingModel model = embeddingClientFactory.buildEmbeddingModel(config);
        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(PROBE_TEXT), null));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()
                || response.getResults().get(0).getOutput() == null) {
            throw new IllegalStateException("embedding 返回为空，请检查 apiModelName");
        }
        int dimension = response.getResults().get(0).getOutput().length;
        ModelTestResult result = new ModelTestResult();
        result.setDimension(dimension);
        result.setMessage("调用成功，向量维度 " + dimension);
        return result;
    }

    /** 取最内层可读的异常消息；业务异常直接用自己的对外消息。 */
    private static String resolveError(Exception e) {
        if (e instanceof BusinessException) {
            return e.getMessage();
        }
        Throwable current = e;
        String message = null;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return message != null ? message : e.getClass().getSimpleName();
    }

    /** 截断回复，避免把整段输出塞进提示。 */
    private static String truncate(String text) {
        return text.length() <= REPLY_PREVIEW_LENGTH
                ? text
                : text.substring(0, REPLY_PREVIEW_LENGTH) + "…";
    }
}
