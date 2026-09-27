package org.deepstack.ai.knowledge.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库分片分页查询请求
 *
 * <p>仅支持按文档 id 维度查询；按 KB 维度查询走全文 / 向量检索接口。</p>
 *
 */
@Data
public class KnowledgeChunkPageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 20;

    @NotNull(message = "文档 id 不能为空")
    private Long documentId;

    /**
     * 内容关键字模糊匹配
     */
    private String keyword;
}
