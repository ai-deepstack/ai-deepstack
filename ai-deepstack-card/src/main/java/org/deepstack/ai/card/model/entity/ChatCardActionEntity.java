package org.deepstack.ai.card.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 卡片动作历史实体
 * <p>
 * 记录用户对卡片的 confirm / edit / reject 动作。
 * </p>
 *
 */
@Data
@TableName(value = "ai_chat_card_action", autoResultMap = true)
public class ChatCardActionEntity implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 卡片业务 ID */
    private String cardId;

    /** 动作：见 {@link org.deepstack.ai.kernel.enums.card.CardActionTypeEnum} */
    private Integer action;

    /** 编辑后的载荷（edit 时） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> modifiedPayload;

    /** 操作人 ID */
    private String operatorId;

    // ===== 公共字段 =====

    /** 逻辑删除：0正常 1已删（插入自动填充 0，业务勿赋值） */
    @TableLogic
    private Integer isDel;

    /** 创建人 ID */
    private String creatorId;

    /** 创建人名称 */
    private String creator;

    /** 修改人 ID */
    private String modifierId;

    /** 修改人名称 */
    private String modifier;

        /** 创建时间（插入自动填充 / DB 默认，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

        /** 更新时间（插入/更新自动填充 / DB 触发器，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
