package org.deepstack.ai.knowledge.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文档分页查询请求
 *
 */
@Data
public class KnowledgeDocumentPageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    @NotNull(message = "知识库 id 不能为空")
    private Long knowledgeBaseId;

    /**
     * 来源类型过滤：MANUAL / FILE / URL
     */
    @Pattern(regexp = "MANUAL|FILE|URL", message = "来源类型仅支持 MANUAL / FILE / URL")
    private Integer sourceType;

    /** 解析状态码过滤（DocParseStatusEnum） */
    private Integer parseStatus;

    /** 向量化状态码过滤（DocEmbedStatusEnum） */
    private Integer embedStatus;

    /**
     * 关键字模糊匹配 title / tags
     */
    private String keyword;
}
