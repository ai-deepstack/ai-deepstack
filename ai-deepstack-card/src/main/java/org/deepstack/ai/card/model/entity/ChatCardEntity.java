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
import java.util.List;
import java.util.Map;

/**
 * 对话结构化卡片实体
 * <p>
 * 记录对话中产出的结构化卡片；用户可执行 confirm / edit / reject。
 * </p>
 *
 */
@Data
@TableName(value = "ai_chat_card", autoResultMap = true)
public class ChatCardEntity implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 卡片业务 ID（唯一） */
    private String cardId;

    /** 所属会话 ID */
    private String conversationId;

    /** 工作流 thread ID（HITL 预留） */
    private String threadId;

    /** 检查点 ID（HITL 预留） */
    private String checkpointId;

    /** 用户 ID */
    private String userId;

    /** 卡片类型（业务自定义） */
    private String cardType;

    /** 卡片标题 */
    private String title;

    /** 卡片载荷 JSON */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /** 可执行动作定义 JSON */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> actions;

    /** 状态码：见 {@link org.deepstack.ai.kernel.enums.card.ChatCardStatusEnum} */
    private Integer status;

    /** 父卡片 ID（重新生成链路） */
    private String parentCardId;

    /** 过期时间 */
    private LocalDateTime expiresAt;

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
