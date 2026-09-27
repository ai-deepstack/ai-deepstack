package org.deepstack.ai.agent.model.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 智能体修改请求
 */
@Data
public class AiAgentUpdateRequest implements Serializable {

    @NotNull(message = "智能体 id 不能为空")
    private Long id;

    @Size(max = 128, message = "智能体名称长度不能超过 128")
    private String agentName;

    @Size(max = 64, message = "模型编码长度不能超过 64")
    private String modelCode;

    private String systemPrompt;

    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private BigDecimal temperature;

    @Min(value = 1, message = "最大 token 数必须大于 0")
    private Integer maxTokens;

    @DecimalMin(value = "0.0", message = "topP 不能小于 0")
    @DecimalMax(value = "1.0", message = "topP 不能大于 1")
    private BigDecimal topP;

    @Min(value = 0, message = "记忆窗口不能为负")
    private Integer memoryMaxMessages;

    private Integer enableMemory;

    /** 响应格式码：见 ResponseFormatEnum */
    private Integer responseFormat;

    private String responseSchema;

    private Integer enabled;

    /** 编排模式码：见 OrchestrateModeEnum */
    private Integer orchestrateMode;

    /** 图定义 JSON（GRAPH 模式）；null 表示不修改 */
    private String graphDefinition;

    /**
     * 绑定的知识库编码（按优先级排序）。
     * null = 不修改；空列表 = 清除全部绑定。
     */
    private List<String> knowledgeBaseCodes;

    /** 追踪模式码：见 TraceModeEnum */
    private Integer traceMode;

    private Integer streamProgress;

    /** 封面图 URL；传空串可清空 */
    @Size(max = 512, message = "封面图 URL 长度不能超过 512")
    private String coverUrl;

    /** 智能体每秒请求上限；null=不改；传 0 表示清除限制 */
    @Min(value = 0, message = "QPS 不能为负")
    private Integer quotaQps;

    /** 智能体并发运行上限；null=不改；传 0 表示清除限制 */
    @Min(value = 0, message = "并发上限不能为负")
    private Integer quotaConcurrency;

    /** 智能体每日 token 上限；null=不改；传 0 表示清除限制 */
    @Min(value = 0, message = "每日 token 上限不能为负")
    private Integer quotaDailyTokens;

    /** HITL 等待超时分钟；null=不改；传负数或 0 表示清除（走全局） */
    private Integer hitlTimeoutMinutes;
}
