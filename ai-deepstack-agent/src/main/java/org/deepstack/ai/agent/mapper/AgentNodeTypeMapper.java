package org.deepstack.ai.agent.mapper;

import org.deepstack.ai.agent.model.entity.AgentNodeType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流节点类型定义 Mapper
 *
 */
@Mapper
public interface AgentNodeTypeMapper extends BaseMapper<AgentNodeType> {
}
