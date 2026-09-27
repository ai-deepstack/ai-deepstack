package org.deepstack.ai.agent.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class AiIntentUpdateRequest implements Serializable {

    @NotNull(message = "id 不能为空")
    private Long id;

    @Size(max = 64, message = "意图编码长度不能超过 64")
    private String intentCode;

    @Size(max = 128, message = "意图名称长度不能超过 128")
    private String intentName;

    @Size(max = 2000, message = "描述长度不能超过 2000")
    private String description;

    private Integer sortOrder;

    private Integer enabled;
}
