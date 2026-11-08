package org.deepstack.ai.aimodel.model.dto.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型连通性测试请求。
 * <p>传 {@code id} 测已保存的模型；同时带上表单字段可覆盖，用于测试未保存的修改。
 * 编辑时不改 Key 的场景下 {@code apiKey} 留空，回落到库中已存的 Key。</p>
 */
@Data
public class AiModelTestRequest implements Serializable {

    /** 已保存模型 id；为空表示测未保存的临时配置 */
    private Long id;

    /** 模型类型：0 CHAT / 1 EMBEDDING */
    private Integer modelType;

    /** 模型编码（仅用于日志与错误信息） */
    private String modelCode;

    /** OpenAI 兼容 Base URL */
    private String baseUrl;

    /** API Key；为空且 id 存在时使用库中已存的 Key */
    private String apiKey;

    /** 上游实际模型名 */
    private String apiModelName;

    /** 自定义请求头 JSON */
    private String customHeaders;
}
