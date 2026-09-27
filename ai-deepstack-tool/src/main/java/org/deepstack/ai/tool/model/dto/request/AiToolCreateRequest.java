package org.deepstack.ai.tool.model.dto.request;


import org.deepstack.ai.kernel.enums.common.YesNo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 工具新建请求
 * <p>
 * 工具的参数 schema 从 Java {@code @Tool} 方法签名反射生成，此处不存 param_schema。
 * description 为空时用 Java {@code @Tool} 注解的 description 兜底。
 * </p>
 *
 */
@Data
public class AiToolCreateRequest implements Serializable {

    /**
     * 工具编码（唯一业务标识）
     */
    @NotBlank(message = "工具编码不能为空")
    @Size(max = 64, message = "工具编码长度不能超过 64")
    private String toolCode;

    /**
     * 工具显示名称
     */
    @NotBlank(message = "工具名称不能为空")
    @Size(max = 128, message = "工具名称长度不能超过 128")
    private String toolName;

    /**
     * 给 LLM 的工具描述；为空时用 Java @Tool 注解兜底
     */
    @Size(max = 4000, message = "工具描述长度不能超过 4000")
    private String description;

    /**
     * Spring 容器中 ToolHandler Bean 的名称
     */
    @NotBlank(message = "handler Bean 名不能为空")
    @Size(max = 128, message = "handler Bean 名长度不能超过 128")
    private String handlerBean;

    /**
     * 是否启用，默认 true
     */
    private Integer enabled = YesNo.YES.getCode();
}
