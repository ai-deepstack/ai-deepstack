package org.deepstack.ai.engine;

import lombok.Data;

import java.io.Serializable;

/**
 * 可编译的图定义（纯 DTO，无 DB 表、不依赖 Agent 实体）。
 * <p>
 * 由编排侧（agent/chat）从 {@code ai_agent} 组装，交给 runtime 编译执行。
 * </p>
 */
@Data
public class GraphSpec implements Serializable {

    /** 通常为 agentId，用于缓存键与 run 日志关联 */
    private Long id;

    private String code;

    private String name;

    private String description;

    /** 图定义 JSON */
    private String definition;

    private String graphType;

    private Integer version;

    private Integer enabled;

    /**
     * 智能体级默认模型编码；llm/intent 节点未配置 modelCode 时由编译器回填。
     */
    private String defaultModelCode;
}
