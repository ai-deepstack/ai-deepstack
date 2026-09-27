package org.deepstack.ai.agent.graph;

import org.deepstack.ai.agent.model.entity.AiAgentGraphVersion;
import org.deepstack.ai.engine.GraphSpec;

import java.util.List;
import java.util.Map;

/**
 * 智能体编排图服务：草稿读写、发布/回滚、片段插入。
 */
public interface AgentGraphService {

    /**
     * 读取智能体当前草稿图定义，并封装为 {@link GraphSpec}。
     *
     * @param agentId 智能体主键
     * @return 图规格（definition=草稿）
     */
    GraphSpec getGraph(Long agentId);

    /**
     * 仅更新草稿 {@code graphDefinition}，并在必要时将编排模式切到 GRAPH。
     * <p>更新成功后会使运行时图缓存失效。</p>
     *
     * @param agentId    智能体主键
     * @param definition 图定义对象（序列化为 JSON 落库）
     */
    void updateGraph(Long agentId, Map<String, Object> definition);

    /**
     * 发布：将草稿复制到已发布字段，递增 publishedVersion，并写入版本历史。
     *
     * @param agentId 智能体主键
     * @param remark  发布备注（可空）
     * @return 新的已发布版本号
     */
    Integer publish(Long agentId, String remark);

    /**
     * 列出智能体已发布图版本历史（版本号降序）。
     *
     * @param agentId 智能体主键
     * @return 版本行列表
     */
    List<AiAgentGraphVersion> listVersions(Long agentId);

    /**
     * 回滚：将指定历史版本写入 published，并同步到草稿，使画布与线上一致。
     *
     * @param agentId 智能体主键
     * @param version 目标历史版本号
     */
    void rollback(Long agentId, Integer version);

    /**
     * 将节点/边片段合并进草稿（节点 id 重新生成短 UUID）。
     *
     * @param agentId  智能体主键
     * @param nodes    待插入节点（可空）
     * @param edges    待插入边（可空；source/target 按原 id 映射到新 id）
     */
    void insertFragment(Long agentId, List<Map<String, Object>> nodes, List<Map<String, Object>> edges);
}
