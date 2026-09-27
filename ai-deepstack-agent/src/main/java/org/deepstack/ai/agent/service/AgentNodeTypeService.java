package org.deepstack.ai.agent.service;

import org.deepstack.ai.agent.model.entity.AgentNodeType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 工作流节点类型定义服务
 * <p>
 * 从 agent_node_type 表加载节点类型定义，支持缓存刷新。
 * 动态选项注入（模型/知识库/工具）由 {@link org.deepstack.ai.agent.graph.NodeTypeRegistry} 负责。
 * </p>
 *
 */
public interface AgentNodeTypeService extends IService<AgentNodeType> {

    /**
     * 查询所有启用的节点类型（按 sort_order 排序）
     */
    List<AgentNodeType> listEnabled();

    /**
     * 按 typeCode 查询启用的节点类型
     */
    AgentNodeType getByTypeCode(String typeCode);

    /**
     * 分页查询
     */
    com.baomidou.mybatisplus.core.metadata.IPage<AgentNodeType> page(
            int pageNum, int pageSize, String category, Boolean enabled);

    /**
     * 新建节点类型
     */
    Long create(AgentNodeType entity);

    /**
     * 修改节点类型
     */
    void update(AgentNodeType entity);

    /**
     * 启用节点类型
     */
    void enable(Long id);

    /**
     * 禁用节点类型
     */
    void disable(Long id);

    /**
     * 刷新缓存
     */
    void refreshCache();
}
