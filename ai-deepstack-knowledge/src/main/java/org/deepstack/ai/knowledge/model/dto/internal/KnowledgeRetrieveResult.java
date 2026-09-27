package org.deepstack.ai.knowledge.model.dto.internal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单库召回结果：多库并行 Facade 按 {@code baseCodes} 下标顺序组装。
 */
@Data
public class KnowledgeRetrieveResult {

    /** 知识库编码 */
    private String baseCode;

    /** 知识库名称（展示/拼 prompt 用） */
    private String baseName;

    /** 命中文本块（已按该库 topK 截断） */
    private List<String> texts = new ArrayList<>();

    /** 本库是否超时/异常（异常时 texts 为空，不阻断其他库） */
    private boolean failed;

    /** 失败原因摘要，便于日志排查 */
    private String errorMessage;

    /** 本库耗时毫秒 */
    private long costMs;
}
