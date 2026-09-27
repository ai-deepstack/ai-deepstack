package org.deepstack.ai.tool.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 工具修改请求
 *
 */
@Data
public class AiToolUpdateRequest implements Serializable {

    @NotNull(message = "id 不能为空")
    private Long id;

    @Size(max = 64, message = "工具编码长度不能超过 64")
    private String toolCode;

    @Size(max = 128, message = "工具名称长度不能超过 128")
    private String toolName;

    /**
     * 给 LLM 的工具描述；传 null 表示不修改，传空串表示清空（用 @Tool 兜底）
     */
    @Size(max = 4000, message = "工具描述长度不能超过 4000")
    private String description;

    @Size(max = 128, message = "handler Bean 名长度不能超过 128")
    private String handlerBean;

    private Integer enabled;
}
