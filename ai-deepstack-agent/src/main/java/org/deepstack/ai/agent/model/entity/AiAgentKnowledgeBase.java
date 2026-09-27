package org.deepstack.ai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体与知识库绑定实体
 */
@Data
@TableName("ai_agent_knowledge_base")
public class AiAgentKnowledgeBase implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 智能体 ID（ai_agent.id） */
    private Long agentId;

    /** 知识库编码 */
    private String knowledgeBaseCode;

    /** 检索优先级（同智能体内唯一） */
    private Integer priority;

    /** 创建时间 */
    private LocalDateTime createTime;
}
