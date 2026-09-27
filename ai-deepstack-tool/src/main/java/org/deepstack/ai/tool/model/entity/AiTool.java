package org.deepstack.ai.tool.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LLM 工具目录实体
 * <p>
 * {@code handlerBean} 对应 Spring Bean；{@code description} 为空时用 Java {@code @Tool} 注解兜底。
 * </p>
 *
 */
@Data
@TableName("ai_tool")
public class AiTool implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工具编码（唯一） */
    private String toolCode;

    /** 工具名称 */
    private String toolName;

    /** 工具说明（供 LLM 选择） */
    private String description;

    /** Spring Bean 名或处理器标识（MCP 工具可空） */
    private String handlerBean;

    /** 来源：见 {@link org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum} */
    private Integer sourceType;

    /** MCP 连接编码（source_type=MCP） */
    private String mcpConnectionCode;

    /** MCP 远端工具原名 */
    private String mcpToolName;

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
