package org.deepstack.ai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流节点类型目录实体（DB 驱动）
 * <p>
 * 定义节点默认属性、属性表单与端口约束；前端 /node-types 从此表读取。
 * </p>
 *
 */
@Data
@TableName(value = "agent_node_type")
public class AgentNodeType implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 类型编码，如 llm-node */
    private String typeCode;

    /** 展示名称 */
    private String label;

    /** 图标标识 */
    private String icon;

    /** 分类：basic / ai / tool / flow */
    private String category;

    /** 默认属性 JSON */
    private String defaultProps;

    /** 属性表单字段定义 JSON */
    private String propertyFields;

    /** 最大入边数，-1 表示不限 */
    private Integer maxInPorts;

    /** 最大出边数，-1 表示不限 */
    private Integer maxOutPorts;

    /** 排序 */
    private Integer sortOrder;

    /** 是否启用：1启用 0停用 */
    private Integer enabled;

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
