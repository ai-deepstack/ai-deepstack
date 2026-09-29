package org.deepstack.ai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import org.deepstack.ai.kernel.mybatis.PostgresJsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能体配置实体（一等公民）。
 * <p>
 * {@code orchestrateMode=CHAT}：直连模型+工具+RAG；
 * {@code orchestrateMode=GRAPH}：使用 {@code graphDefinition} 编译执行 LangGraph。
 * </p>
 */
@Data
@TableName(value = "ai_agent", autoResultMap = true)
public class AiAgent implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 智能体编码（唯一） */
    private String agentCode;

    /** 智能体名称 */
    private String agentName;

    /** 系统提示词 */
    private String systemPrompt;

    /** 绑定的 CHAT 模型编码（ai_model.model_code） */
    private String modelCode;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 最大生成 token 数 */
    private Integer maxTokens;

    /** nucleus sampling top_p */
    private BigDecimal topP;

    /** 短期记忆保留消息条数 */
    private Integer memoryMaxMessages;

    /** 是否启用短期记忆：1是 0否 */
    private Integer enableMemory;

    /** 响应格式：见 {@link org.deepstack.ai.kernel.enums.model.ResponseFormatEnum} */
    private Integer responseFormat;

    /** 结构化输出 JSON Schema（可选） */
    private String responseSchema;

    /** 是否启用：1启用 0停用 */
    private Integer enabled;

    /** 是否启用长期记忆（预留） */
    private Integer enableLongTermMemory;

    /** 是否启用图谱记忆（预留） */
    private Integer enableGraphMemory;

    /** 编排模式：见 {@link org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum} */
    private Integer orchestrateMode;

    /** LangGraph 图定义草稿 JSON（GRAPH 模式；试跑/画布读写此字段） */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private String graphDefinition;

    /** 已发布图定义 JSON（/api/chat GRAPH 只跑此字段） */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private String publishedGraphDefinition;

    /** 已发布版本号（与乐观锁 graphVersion 独立） */
    private Integer publishedVersion;

    /** 智能体每秒请求上限；空=不限 */
    private Integer quotaQps;

    /** 智能体并发运行上限；空=不限 */
    private Integer quotaConcurrency;

    /** 智能体每日 token 上限；空=不限 */
    private Integer quotaDailyTokens;

    /** HITL 等待超时分钟；空则用全局配置 */
    private Integer hitlTimeoutMinutes;

    /** 图定义草稿乐观锁版本号 */
    @Version
    private Integer graphVersion;

    /** 追踪模式：见 {@link org.deepstack.ai.kernel.enums.agent.TraceModeEnum} */
    private Integer traceMode;

    /** 是否推送节点进度 SSE：1是 0否 */
    private Integer streamProgress;

    /** 封面图 URL；为空时前端使用默认封面 */
    private String coverUrl;

    // ===== 公共字段 =====

    /** 逻辑删除：0正常 1已删（插入自动填充 0，业务勿赋值） */
    @TableLogic
    private Integer isDel;

    /** 创建人 ID */
    private Long creatorId;

    /** 创建人名称 */
    private String creator;

    /** 修改人 ID */
    private Long modifierId;

    /** 修改人名称 */
    private String modifier;

    /** 创建时间（插入自动填充 / DB 默认，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新自动填充 / DB 触发器，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
