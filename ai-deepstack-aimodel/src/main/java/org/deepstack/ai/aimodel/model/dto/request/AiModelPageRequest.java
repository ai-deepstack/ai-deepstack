package org.deepstack.ai.aimodel.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 模型分页查询请求（CHAT + EMBEDDING）
 *
 * <p>v1.2 由 {@code ChatModelPageRequest} 重命名而来，新增 {@code modelType} 过滤字段。</p>
 *
 */
@Data
public class AiModelPageRequest implements Serializable {

    /**
     * 页码（从 1 开始）
     */
    @Min(1)
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    /**
     * 模型类型过滤：见 ModelTypeEnum
     */
    private Integer modelType;

    /**
     * 供应商过滤：deepseek / qwen / openai / zhipu / moonshot
     */
    private String provider;

    /**
     * 启用状态过滤
     */
    private Integer enabled;

    /**
     * 关键字模糊匹配 modelCode / modelName
     */
    private String keyword;
}
