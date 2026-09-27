package org.deepstack.ai.tool.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 工具分页查询请求
 *
 */
@Data
public class AiToolPageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    /**
     * 启用状态过滤
     */
    private Integer enabled;

    /**
     * 关键字模糊匹配 toolCode / toolName
     */
    private String keyword;
}
