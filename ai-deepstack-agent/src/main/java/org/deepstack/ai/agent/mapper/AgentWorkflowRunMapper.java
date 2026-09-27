package org.deepstack.ai.agent.mapper;

import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 工作流执行日志 Mapper
 *
 */
@Mapper
public interface AgentWorkflowRunMapper extends BaseMapper<AgentWorkflowRun> {
}
