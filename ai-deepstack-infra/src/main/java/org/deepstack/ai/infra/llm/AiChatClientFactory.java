package org.deepstack.ai.infra.llm;

import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.model.ModelTypeEnum;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openai.core.Timeout;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat 客户端工厂：按 {@code ai_model.model_code} 构建并缓存 {@link OpenAiChatModel}。
 * <p>
 * 不同供应商（DeepSeek / Qwen / 智谱等）通过不同的 base-url + api-key 区分，
 * 全部走 OpenAI 兼容协议。HTTP connect/read 超时优先读 {@link SysConfigPort}，
 * yml {@link DeepstackLlmProperties} 作兜底。
 * </p>
 * <p>
 * 缓存键由 {@code modelCode} + 关键字段（baseUrl/apiKey/apiModelName）哈希组成。
 * DB 中模型配置变更后由 {@link AiModelClientCacheEvictListener} 调用 {@link #evict(String)}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(DeepstackLlmProperties.class)
public class AiChatClientFactory {

    private final AiModelService aiModelService;
    private final DeepstackLlmProperties llmProperties;
    private final SysConfigPort sysConfigPort;

    /** 模型实例缓存：key=cacheKey(modelCode + version)，value=OpenAiChatModel */
    private final ConcurrentHashMap<String, OpenAiChatModel> instanceCache = new ConcurrentHashMap<>();

    /**
     * 按 modelCode 取得对应的 ChatModel 实例（带缓存）。
     */
    public OpenAiChatModel getChatModel(String modelCode) {
        log.info("getChatModel: modelCode={}", modelCode);
        AiModel config = requireEnabledChat(modelCode);
        String key = buildCacheKey(config);
        boolean cacheHit = instanceCache.containsKey(key);
        OpenAiChatModel model = instanceCache.computeIfAbsent(key, k -> buildChatModel(config));
        if (cacheHit) {
            log.debug("getChatModel cache hit: modelCode={}, key={}", modelCode, key);
        }
        return model;
    }

    /**
     * 按 modelCode 取得供应商侧 apiModelName。
     */
    public String getApiModelName(String modelCode) {
        AiModel config = requireEnabledChat(modelCode);
        return config.getApiModelName();
    }

    /** 按 modelCode 失效对应客户端缓存。 */
    public void evict(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return;
        }
        String prefix = modelCode + ":";
        instanceCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.info("ChatClient cache evicted: modelCode={}", modelCode);
    }

    /** 清空全部客户端缓存。 */
    public void evictAll() {
        instanceCache.clear();
        log.info("ChatClient cache fully evicted");
    }

    /** 校验并返回已启用的 CHAT 模型。 */
    private AiModel requireEnabledChat(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "modelCode 不能为空");
        }
        AiModel config = aiModelService.getByModelCode(modelCode);
        if (config == null) {
            log.warn("getChatModel 模型不存在或已禁用: modelCode={}", modelCode);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "模型不存在或已禁用: modelCode=" + modelCode);
        }
        if (config.getModelType() == null
                || config.getModelType() != ModelTypeEnum.CHAT.getCode()) {
            log.warn("getChatModel 类型不是 CHAT: modelCode={}, modelType={}",
                    modelCode, config.getModelType());
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "模型类型不是 CHAT: modelCode=" + modelCode + ", modelType=" + config.getModelType());
        }
        if (YesNo.isNo(config.getEnabled())) {
            log.warn("getChatModel 模型已禁用: modelCode={}", modelCode);
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "模型已禁用: modelCode=" + modelCode);
        }
        return config;
    }

    /** 按模型关键字段生成缓存键。 */
    private String buildCacheKey(AiModel config) {
        return config.getModelCode() + ":"
                + safeHash(config.getBaseUrl())
                + ":" + safeHash(config.getApiKey())
                + ":" + safeHash(config.getApiModelName())
                + ":" + resolveConnectTimeoutMs()
                + ":" + resolveReadTimeoutMs();
    }

    /** 对敏感字段取哈希，避免把密钥写入缓存键明文。 */
    private String safeHash(String s) {
        return s == null ? "null" : String.valueOf(s.hashCode());
    }

    /** 解析连接超时（毫秒）。 */
    private int resolveConnectTimeoutMs() {
        return Math.max(1_000, sysConfigPort.getInt(
                SysConfigKeys.LLM_CONNECT_TIMEOUT_MS, llmProperties.getConnectTimeoutMs()));
    }

    /** 解析读取超时（毫秒）。 */
    private int resolveReadTimeoutMs() {
        return Math.max(5_000, sysConfigPort.getInt(
                SysConfigKeys.LLM_READ_TIMEOUT_MS, llmProperties.getReadTimeoutMs()));
    }

    /** 按模型配置构建 Chat 客户端。 */
    private OpenAiChatModel buildChatModel(AiModel config) {
        int connectMs = resolveConnectTimeoutMs();
        int readMs = resolveReadTimeoutMs();
        log.info("Building OpenAiChatModel: modelCode={}, provider={}, baseUrl={}, apiModelName={}, connectMs={}, readMs={}",
                config.getModelCode(), config.getProvider(), config.getBaseUrl(),
                config.getApiModelName(), connectMs, readMs);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .model(config.getApiModelName())
                .timeout(Duration.ofMillis(readMs))
                .build();

        OpenAiHttpClientBuilderCustomizer timeouts = builder -> builder.timeout(
                Timeout.builder()
                        .connect(Duration.ofMillis(connectMs))
                        .read(Duration.ofMillis(readMs))
                        .request(Duration.ofMillis(readMs))
                        .build());

        return OpenAiChatModel.builder()
                .options(options)
                .httpClientBuilderCustomizer(timeouts)
                .build();
    }
}
