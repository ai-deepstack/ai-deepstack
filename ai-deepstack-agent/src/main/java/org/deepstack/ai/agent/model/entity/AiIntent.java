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
 * 租户级意图字典实体。
 * <p>
 * 当前平台尚未接入真实租户时，{@code tenantId} 固定为 {@code 0}。
 * </p>
 */
@Data
@TableName("ai_intent")
public class AiIntent implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户 ID；默认 0 */
    private Long tenantId;

    /** 意图编码（路由/LLM 返回值） */
    private String intentCode;

    /** 意图显示名 */
    private String intentName;

    /** 说明 */
    private String description;

    /** 排序，越小越靠前 */
    private Integer sortOrder;

    /** 是否启用：1启用 0停用 */
    private Integer enabled;

    @TableLogic
    private Integer isDel;

    private Long creatorId;
    private String creator;
    private Long modifierId;
    private String modifier;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
