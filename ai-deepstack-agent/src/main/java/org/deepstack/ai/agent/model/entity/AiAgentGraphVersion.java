package org.deepstack.ai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.deepstack.ai.kernel.mybatis.PostgresJsonbTypeHandler;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体已发布图版本历史（供回滚）。
 */
@Data
@TableName(value = "ai_agent_graph_version", autoResultMap = true)
public class AiAgentGraphVersion implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 智能体主键 */
    private Long agentId;

    /** 发布版本号 */
    private Integer version;

    /** 该版本图定义 JSON */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private String definition;

    /** 发布备注 */
    private String remark;

    /** 创建人 ID */
    private Long creatorId;

    /** 创建人名称 */
    private String creator;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
