package org.deepstack.ai.agent.model.dto.request;

import org.deepstack.ai.kernel.enums.common.YesNo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class AiIntentCreateRequest implements Serializable {

    /** 租户 ID；不传则使用默认租户 0 */
    private Long tenantId;

    @NotBlank(message = "意图编码不能为空")
    @Size(max = 64, message = "意图编码长度不能超过 64")
    private String intentCode;

    @NotBlank(message = "意图名称不能为空")
    @Size(max = 128, message = "意图名称长度不能超过 128")
    private String intentName;

    @Size(max = 2000, message = "描述长度不能超过 2000")
    private String description;

    private Integer sortOrder = 0;

    private Integer enabled = YesNo.YES.getCode();
}
