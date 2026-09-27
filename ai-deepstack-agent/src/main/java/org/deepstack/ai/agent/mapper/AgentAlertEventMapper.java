package org.deepstack.ai.agent.mapper;

import org.deepstack.ai.agent.model.entity.AgentAlertEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警事件 Mapper。
 */
@Mapper
public interface AgentAlertEventMapper extends BaseMapper<AgentAlertEvent> {
}
