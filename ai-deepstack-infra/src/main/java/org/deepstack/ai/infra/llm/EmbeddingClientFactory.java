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
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 客户端工厂：按 {@code ai_model.model_code} 构建并缓存 {@link OpenAiEmbeddingModel}。
 * <p>
 * 强制 dimensions=1024，与 pgvector 列定义对齐。HTTP 超时优先读 {@link SysConfigPort}，
 * yml {@link DeepstackLlmProperties} 作兜底。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClientFactory {

    public static final int FIXED_DIMENSIONS = 1024;

    private final AiModelService aiModelService;
    private final DeepstackLlmProperties llmProperties;
    private final SysConfigPort sysConfigPort;

    private final ConcurrentHashMap<String, OpenAiEmbeddingModel> instanceCache = new ConcurrentHashMap<>();

    /** 返回 EmbeddingModel。 */
    public OpenAiEmbeddingModel getEmbeddingModel(String modelCode) {
        log.info("getEmbeddingModel: modelCode={}", modelCode);
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "modelCode 不能为空");
        }

        AiModel config = aiModelService.getByModelCode(modelCode);
        if (config == null) {
            log.warn("getEmbeddingModel 模型不存在或已禁用: modelCode={}", modelCode);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "模型不存在或已禁用: modelCode=" + modelCode);
        }
        if (config.getModelType() == null
                || config.getModelType() != ModelTypeEnum.EMBEDDING.getCode()) {
            log.warn("getEmbeddingModel 类型不是 EMBEDDING: modelCode={}, modelType={}",
                    modelCode, config.getModelType());
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "模型类型不是 EMBEDDING: modelCode=" + modelCode + ", modelType=" + config.getModelType());
        }
        if (YesNo.isNo(config.getEnabled())) {
            log.warn("getEmbeddingModel 模型已禁用: modelCode={}", modelCode);
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "模型已禁用: modelCode=" + modelCode);
        }

        String key = buildCacheKey(config);
        boolean cacheHit = instanceCache.containsKey(key);
        OpenAiEmbeddingModel model = instanceCache.computeIfAbsent(key, k -> buildEmbeddingModel(config));
        if (cacheHit) {
            log.debug("getEmbeddingModel cache hit: modelCode={}, key={}", modelCode, key);
        }
        return model;
    }

    /** 按 modelCode 失效对应客户端缓存。 */
    public void evict(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return;
        }
        String prefix = modelCode + ":";
        instanceCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.info("EmbeddingClient cache evicted: modelCode={}", modelCode);
    }

    /** 清空全部客户端缓存。 */
    public void evictAll() {
        instanceCache.clear();
        log.info("EmbeddingClient cache fully evicted");
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

    /** 按模型配置构建 Embedding 客户端。 */
    private OpenAiEmbeddingModel buildEmbeddingModel(AiModel config) {
        int connectMs = resolveConnectTimeoutMs();
        int readMs = resolveReadTimeoutMs();
        log.info("Building OpenAiEmbeddingModel: modelCode={}, provider={}, baseUrl={}, apiModelName={}, dimensions={}, connectMs={}, readMs={}",
                config.getModelCode(), config.getProvider(), config.getBaseUrl(),
                config.getApiModelName(), FIXED_DIMENSIONS, connectMs, readMs);

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .model(config.getApiModelName())
                .dimensions(FIXED_DIMENSIONS)
                .timeout(Duration.ofMillis(readMs))
                .build();

        OpenAiHttpClientBuilderCustomizer timeouts = builder -> builder.timeout(
                Timeout.builder()
                        .connect(Duration.ofMillis(connectMs))
                        .read(Duration.ofMillis(readMs))
                        .request(Duration.ofMillis(readMs))
                        .build());

        return OpenAiEmbeddingModel.builder()
                .options(options)
                .httpClientBuilderCustomizer(timeouts)
                .build();
    }
}
