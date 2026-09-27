package org.deepstack.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.deepstack.ai.agent.model.entity.AiAgentGraphVersion;

/**
 * 智能体图版本历史 Mapper。
 */
@Mapper
public interface AiAgentGraphVersionMapper extends BaseMapper<AiAgentGraphVersion> {
}
