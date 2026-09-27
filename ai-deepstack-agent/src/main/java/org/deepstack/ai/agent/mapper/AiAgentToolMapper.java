package org.deepstack.ai.agent.mapper;

import org.deepstack.ai.agent.model.entity.AiAgentTool;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能体-工具绑定 Mapper
 */
@Mapper
public interface AiAgentToolMapper extends BaseMapper<AiAgentTool> {

    @Select("""
            SELECT aat.tool_id
            FROM ai_agent_tool aat
            JOIN ai_tool t ON t.id = aat.tool_id AND t.is_del = 0 AND t.enabled = 1
            WHERE aat.agent_id = #{agentId}
              AND aat.is_del = 0
              AND aat.enabled = 1
            ORDER BY aat.priority ASC
            """)
    List<Long> selectEnabledToolIdsByAgentId(@Param("agentId") Long agentId);

    @Select("""
            SELECT t.tool_code
            FROM ai_agent_tool aat
            JOIN ai_tool t ON t.id = aat.tool_id AND t.is_del = 0 AND t.enabled = 1
            WHERE aat.agent_id = #{agentId}
              AND aat.is_del = 0
              AND aat.enabled = 1
            ORDER BY aat.priority ASC
            """)
    List<String> selectEnabledToolCodesByAgentId(@Param("agentId") Long agentId);

    int deleteByAgentId(@Param("agentId") Long agentId);
}
