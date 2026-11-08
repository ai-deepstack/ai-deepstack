package org.deepstack.ai.aimodel.model.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型连通性测试结果。
 * <p>接口不可用也返回成功响应，由 {@code ok} 区分，前端直接展示 {@code message}。</p>
 */
@Data
public class ModelTestResult implements Serializable {

    /** 是否可用 */
    private Boolean ok;

    /** 本次调用耗时（毫秒） */
    private Long latencyMs;

    /** 成功摘要或失败原因 */
    private String message;

    /** EMBEDDING 返回的向量维度；CHAT 为 null */
    private Integer dimension;
}
