package org.deepstack.ai.knowledge.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库分片管理响应
 *
 * <p>{@code embedding} 字段（1024 维向量）不在管理接口返回，避免传输巨量数据。</p>
 *
 */
@Data
public class KnowledgeChunkResponse implements Serializable {

    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    /**
     * 是否已生成向量（业务字段：embedding 字段非空）
     */
    private Integer hasEmbedding;

    /** 是否已有向量（是/否） */
    private String hasEmbeddingName;

    private String metadata;

    private LocalDateTime createTime;
}
