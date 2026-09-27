package org.deepstack.ai.agent.mapper;

import org.deepstack.ai.agent.model.entity.AiAgentKnowledgeBase;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能体-知识库关联 Mapper
 */
@Mapper
public interface AiAgentKnowledgeBaseMapper extends BaseMapper<AiAgentKnowledgeBase> {

    @Select("SELECT knowledge_base_code FROM ai_agent_knowledge_base " +
            "WHERE agent_id = #{agentId} " +
            "ORDER BY priority ASC")
    List<String> selectBaseCodesByAgentId(@Param("agentId") Long agentId);

    @Delete("DELETE FROM ai_agent_knowledge_base WHERE agent_id = #{agentId}")
    int deleteByAgentId(@Param("agentId") Long agentId);
}
