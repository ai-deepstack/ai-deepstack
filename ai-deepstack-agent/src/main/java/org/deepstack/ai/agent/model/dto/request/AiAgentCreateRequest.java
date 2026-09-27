package org.deepstack.ai.agent.model.dto.request;

import org.deepstack.ai.kernel.enums.common.YesNo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 智能体新建请求
 */
@Data
public class AiAgentCreateRequest implements Serializable {

    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 128, message = "智能体名称长度不能超过 128")
    private String agentName;

    @NotBlank(message = "关联模型编码不能为空")
    @Size(max = 64, message = "模型编码长度不能超过 64")
    private String modelCode;

    @NotBlank(message = "系统提示词不能为空")
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

    private Integer enabled = YesNo.YES.getCode();

    /** 编排模式码：见 OrchestrateModeEnum，默认对话 */
    private Integer orchestrateMode = 0;

    /** 图定义 JSON（GRAPH 模式） */
    private String graphDefinition;

    private List<String> knowledgeBaseCodes;

    /** 追踪模式码：见 TraceModeEnum，默认记录 */
    private Integer traceMode = 1;

    private Integer streamProgress = YesNo.NO.getCode();

    /** 封面图 URL */
    @Size(max = 512, message = "封面图 URL 长度不能超过 512")
    private String coverUrl;

    /** 智能体每秒请求上限；空=不限 */
    @Min(value = 0, message = "QPS 不能为负")
    private Integer quotaQps;

    /** 智能体并发运行上限；空=不限 */
    @Min(value = 0, message = "并发上限不能为负")
    private Integer quotaConcurrency;

    /** 智能体每日 token 上限；空=不限 */
    @Min(value = 0, message = "每日 token 上限不能为负")
    private Integer quotaDailyTokens;

    /** HITL 等待超时分钟；空则用全局配置 */
    @Min(value = 1, message = "HITL 超时至少 1 分钟")
    private Integer hitlTimeoutMinutes;
}
