package org.deepstack.ai.agent.mapper;

import org.deepstack.ai.agent.model.entity.AiAgent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 智能体配置 Mapper
 *
 */
@Mapper
public interface AiAgentMapper extends BaseMapper<AiAgent> {

    /**
     * 取指定前缀下最大编码（未逻辑删除记录）。
     * <p>若与已删除编码冲突，创建时由唯一约束失败后重试。</p>
     */
    @Select("SELECT agent_code FROM ai_agent WHERE agent_code LIKE CONCAT(#{prefix}, '%') "
            + "ORDER BY LENGTH(agent_code) DESC, agent_code DESC LIMIT 1")
    String selectMaxAgentCodeByPrefix(@Param("prefix") String prefix);
}
