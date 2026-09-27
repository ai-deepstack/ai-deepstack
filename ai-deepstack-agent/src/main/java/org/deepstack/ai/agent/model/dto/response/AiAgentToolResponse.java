package org.deepstack.ai.agent.model.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 智能体-工具绑定视图
 *
 */
@Data
public class AiAgentToolResponse implements Serializable {

    /**
     * 绑定记录 id
     */
    private Long id;

    /**
     * 智能体 id
     */
    private Long agentId;

    /**
     * 工具 id
     */
    private Long toolId;

    /**
     * 工具编码（联表查询填充）
     */
    private String toolCode;

    /**
     * 工具名称（联表查询填充）
     */
    private String toolName;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 绑定是否生效
     */
    private Integer enabled;

    /** 启用状态中文名 */
    private String enabledName;
}
