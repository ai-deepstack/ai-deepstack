package org.deepstack.ai.chat.model.entity;

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
 * 聊天会话元数据实体
 * <p>
 * conversationId 为业务唯一标识；供跨请求反查 agentCode 等。
 * </p>
 *
 */
@Data
@TableName("ai_chat_conversation")
public class ChatConversation implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话业务 ID（唯一） */
    private String conversationId;

    /** 会话标题 */
    private String title;

    /** 智能体编码 */
    private String agentCode;

    /** 用户 ID */
    private String userId;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    /** 消息条数 */
    private Integer messageCount;

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
