package org.deepstack.ai.knowledge.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库分页查询请求
 *
 */
@Data
public class KnowledgeBasePageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    /**
     * 领域过滤
     */
    private String domain;

    /**
     * 启用状态过滤
     */
    private Integer enabled;

    /**
     * 关键字模糊匹配 baseCode / baseName
     */
    private String keyword;
}
