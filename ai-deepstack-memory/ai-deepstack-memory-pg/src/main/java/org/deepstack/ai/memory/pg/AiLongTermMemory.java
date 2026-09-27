package org.deepstack.ai.memory.pg;

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
 * 长期记忆表实体（PG 主存）。
 */
@Data
@TableName("ai_long_term_memory")
public class AiLongTermMemory implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String userId;

    private String agentCode;

    private String category;

    private String content;

    /** 图谱节点业务 key，如 mem:123 */
    private String graphKey;

    private String conversationId;

    private String entitiesJson;

    @TableLogic
    private Integer isDel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
