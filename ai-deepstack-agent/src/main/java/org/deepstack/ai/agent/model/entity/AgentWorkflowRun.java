package org.deepstack.ai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import org.deepstack.ai.kernel.mybatis.PostgresJsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体运行记录：GRAPH 试跑/对话、CHAT 轮次共用。
 */
@Data
@TableName(value = "agent_workflow_run", autoResultMap = true)
public class AgentWorkflowRun implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 智能体 ID（ai_agent.id） */
    private Long agentId;

    /** 智能体编码（冗余，便于列表筛选） */
    private String agentCode;

    /**
     * 编排模式：见 {@link org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum}（0=CHAT, 1=GRAPH）。
     */
    private Integer orchestrateMode;

    /** 请求关联 ID（与 MDC traceId 一致） */
    private String traceId;

    /** LangGraph threadId（HITL / checkpoint） */
    private String threadId;

    /** 运行时图版本（CHAT 可空） */
    private Integer graphVersion;

    /** 运行时定义快照（CHAT 可空） */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private String definitionSnapshot;

    private String conversationId;

    private String userMessage;

    private String result;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private String nodeExecutions;

    /**
     * 阶段耗时 JSON，键见 {@link org.deepstack.ai.kernel.observability.AgentRunStageKeys}。
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private String stages;

    /** 状态码：见 GraphRunStatusEnum */
    private Integer status;

    private String errorMessage;

    /** 错误分类：见 AgentRunErrorCode.code */
    private String errorCode;

    private Integer totalTokens;

    private Integer durationMs;

    /** HITL 挂起时刻 */
    private LocalDateTime hitlSuspendedAt;

    /** HITL 等待毫秒（resume 时回写） */
    private Integer hitlWaitMs;

    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDel;
}
