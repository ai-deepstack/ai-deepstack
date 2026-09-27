package org.deepstack.ai.agent.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AiIntentResponse implements Serializable {

    private Long id;
    private Long tenantId;
    private String intentCode;
    private String intentName;
    private String description;
    private Integer sortOrder;
    private Integer enabled;

    /** 启用状态中文名 */
    private String enabledName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
