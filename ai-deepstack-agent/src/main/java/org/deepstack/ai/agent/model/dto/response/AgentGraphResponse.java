package org.deepstack.ai.agent.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 图定义响应（编排侧）
 *
 */
@Data
public class AgentGraphResponse implements Serializable {

    private Long id;

    private String workflowCode;

    private String workflowName;

    private String description;

    /**
     * 工作流 JSON 定义（列表页不返回，详情页返回）
     */
    private Map<String, Object> definition;

    private String workflowType;

    private Integer version;

    private Integer enabled;

    /** 启用状态中文名 */
    private String enabledName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
