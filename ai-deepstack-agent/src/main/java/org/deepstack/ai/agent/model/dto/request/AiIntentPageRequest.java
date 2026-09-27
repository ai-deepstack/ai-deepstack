package org.deepstack.ai.agent.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

@Data
public class AiIntentPageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    /** 租户 ID；不传则默认 0 */
    private Long tenantId;

    private Integer enabled;

    private String keyword;
}
