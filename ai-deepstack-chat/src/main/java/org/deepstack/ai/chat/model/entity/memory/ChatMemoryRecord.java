package org.deepstack.ai.chat.model.entity.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话短期记忆消息实体
 * <p>
 * 存储 Spring AI ChatMemory 对话历史；conversationId 逻辑关联，seq 保证同会话顺序。
 * </p>
 *
 */
@Data
@TableName("chat_memory")
public class ChatMemoryRecord implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 ID */
    private String conversationId;

    /** 消息类型：USER / ASSISTANT / SYSTEM / TOOL */
    private String messageType;

    /** 消息正文 */
    private String content;

    /** 会话内序号（递增） */
    private Integer seq;

        /** 创建时间（插入自动填充 / DB 默认，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
