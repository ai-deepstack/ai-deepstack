package org.deepstack.ai.agent.model.dto.userprompt.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户全域提示词分页查询请求
 *
 */
@Data
public class UserPromptPageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    /**
     * 用户 id 模糊匹配
     */
    private String userId;
}
