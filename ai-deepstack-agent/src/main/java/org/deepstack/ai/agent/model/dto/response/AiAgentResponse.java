package org.deepstack.ai.agent.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体管理响应
 */
@Data
public class AiAgentResponse implements Serializable {

    private Long id;

    private String agentCode;

    private String agentName;

    private String modelCode;

    private String modelName;

    private String systemPrompt;

    private BigDecimal temperature;

    private Integer maxTokens;

    private BigDecimal topP;

    private Integer memoryMaxMessages;

    private Integer enableMemory;

    /** 是否启用短期记忆（是/否） */
    private String enableMemoryName;

    private Integer responseFormat;

    /** 响应格式中文名 */
    private String responseFormatName;

    private String responseSchema;

    private Integer enabled;

    /** 启用状态中文名 */
    private String enabledName;

    /** 编排模式码：见 OrchestrateModeEnum */
    private Integer orchestrateMode;

    /** 编排模式中文名 */
    private String orchestrateModeName;

    /** 图定义 JSON（详情可返回；列表可省略） */
    private String graphDefinition;

    private Integer graphVersion;

    /** 已发布版本号 */
    private Integer publishedVersion;

    /** 智能体每秒请求上限；空=不限 */
    private Integer quotaQps;

    /** 智能体并发运行上限；空=不限 */
    private Integer quotaConcurrency;

    /** 智能体每日 token 上限；空=不限 */
    private Integer quotaDailyTokens;

    /** HITL 等待超时分钟；空则用全局配置 */
    private Integer hitlTimeoutMinutes;

    /** 追踪模式码：见 TraceModeEnum */
    private Integer traceMode;

    /** 追踪模式中文名 */
    private String traceModeName;

    private Integer streamProgress;

    /** 是否推送进度（是/否） */
    private String streamProgressName;

    /** 封面图 URL */
    private String coverUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<KnowledgeBaseBinding> knowledgeBases;

    @Data
    public static class KnowledgeBaseBinding implements Serializable {

        private String baseCode;

        private String baseName;

        private Integer priority;
    }
}
