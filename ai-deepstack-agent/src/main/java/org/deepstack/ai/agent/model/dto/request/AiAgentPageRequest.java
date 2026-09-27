package org.deepstack.ai.agent.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 智能体分页查询请求
 *
 */
@Data
public class AiAgentPageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    /**
     * 智能体编码模糊匹配
     */
    private String agentCode;

    /**
     * 启用状态过滤
     */
    private Integer enabled;

    /**
     * 按关联模型编码过滤
     */
    private String modelCode;

    /**
     * 关键字模糊匹配 agentCode / agentName
     */
    private String keyword;
}
