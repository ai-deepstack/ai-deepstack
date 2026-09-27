package org.deepstack.ai.knowledge.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识库管理响应
 *
 */
@Data
public class KnowledgeBaseResponse implements Serializable {

    private Long id;

    private String baseCode;

    private String baseName;

    private String description;

    private String domain;

    /**
     * 关联 ai_model.id（model_type=EMBEDDING）
     */
    private String embeddingModelCode;

    /**
     * 关联 embedding 模型显示名（join 出来）
     */
    private String embeddingModelName;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer topK;

    private BigDecimal similarityThreshold;

    /** 是否启用图谱增强：1是 0否 */
    private Integer enableGraph;

    /** 库级图谱抽取 CHAT 模型编码 */
    private String graphModelCode;

    private Integer enabled;

    /** 启用状态中文名 */
    private String enabledName;

    /**
     * 文档总数（统计字段）
     */
    private Long documentCount;

    /**
     * 分片总数（统计字段）
     */
    private Long chunkCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
