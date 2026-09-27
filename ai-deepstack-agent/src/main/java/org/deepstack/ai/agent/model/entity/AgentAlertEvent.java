package org.deepstack.ai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体告警触发事件（表 agent_alert_event）。
 */
@Data
@TableName("agent_alert_event")
public class AgentAlertEvent implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 规则编码，如 ERROR_RATE / P95 / HITL_BACKLOG / RUNNING */
    private String ruleCode;

    /** 关联智能体编码；全站规则可为 null */
    private String agentCode;

    /** 严重级别：warn / critical 等 */
    private String severity;

    /** 告警标题 */
    private String title;

    /** 详情 */
    private String detail;

    /** 触发时间 */
    private LocalDateTime firedAt;

    /** 是否已确认：1是 0否 */
    private Integer acknowledged;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
