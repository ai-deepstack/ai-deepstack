package org.deepstack.ai.agent.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.agent.mapper.AiAgentGraphVersionMapper;
import org.deepstack.ai.agent.mapper.AiAgentMapper;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.agent.model.entity.AiAgentGraphVersion;
import org.deepstack.ai.engine.GraphSpec;
import org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.observability.GraphDefinitionKeys;
import org.deepstack.ai.runtime.GraphRuntime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link AgentGraphService} 实现：草稿读写、发布/回滚、片段插入，更新后使运行时缓存失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGraphServiceImpl implements AgentGraphService {

    private static final String MSG_OPTIMISTIC_CONFLICT = "图定义已被其他人更新，请刷新后重试";

    private final AiAgentMapper aiAgentMapper;
    private final AiAgentGraphVersionMapper graphVersionMapper;
    private final GraphRuntime graphRuntime;

    /**
     * {@inheritDoc}
     */
    @Override
    public GraphSpec getGraph(Long agentId) {
        log.debug("getGraph: agentId={}", agentId);
        return AgentGraphSpecs.from(requireAgent(agentId));
    }

    /**
     * {@inheritDoc}
     * <p>若当前仍是 CHAT 模式则自动切到 GRAPH。</p>
     */
    @Override
    public void updateGraph(Long agentId, Map<String, Object> definition) {
        AiAgent agent = requireAgent(agentId);
        agent.setGraphDefinition(toJsonString(definition));
        ensureGraphMode(agent);
        saveAgentGraph(agent);
        log.info("Updated agent graph draft: agentId={}, version={}", agentId, agent.getGraphVersion());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer publish(Long agentId, String remark) {
        AiAgent agent = requireAgent(agentId);
        if (!StringUtils.hasText(agent.getGraphDefinition())) {
            log.warn("publish 失败: 草稿为空 agentId={}", agentId);
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(), "草稿图定义为空，无法发布");
        }
        int nextVersion = agent.getPublishedVersion() == null ? 1 : agent.getPublishedVersion() + 1;
        String draft = agent.getGraphDefinition();
        agent.setPublishedGraphDefinition(draft);
        agent.setPublishedVersion(nextVersion);
        saveAgentGraph(agent);

        AiAgentGraphVersion history = new AiAgentGraphVersion();
        history.setAgentId(agentId);
        history.setVersion(nextVersion);
        history.setDefinition(draft);
        history.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        graphVersionMapper.insert(history);

        log.info("Published agent graph: agentId={}, publishedVersion={}, remark={}",
                agentId, nextVersion, remark);
        return nextVersion;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AiAgentGraphVersion> listVersions(Long agentId) {
        requireAgent(agentId);
        List<AiAgentGraphVersion> list = graphVersionMapper.selectList(
                new LambdaQueryWrapper<AiAgentGraphVersion>()
                        .eq(AiAgentGraphVersion::getAgentId, agentId)
                        .orderByDesc(AiAgentGraphVersion::getVersion));
        log.info("listVersions: agentId={}, count={}", agentId, list.size());
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long agentId, Integer version) {
        if (version == null || version < 1) {
            log.warn("rollback 失败: version 非法 agentId={}, version={}", agentId, version);
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "version 非法");
        }
        AiAgent agent = requireAgent(agentId);
        AiAgentGraphVersion history = graphVersionMapper.selectOne(
                new LambdaQueryWrapper<AiAgentGraphVersion>()
                        .eq(AiAgentGraphVersion::getAgentId, agentId)
                        .eq(AiAgentGraphVersion::getVersion, version));
        if (history == null || !StringUtils.hasText(history.getDefinition())) {
            log.warn("rollback 失败: 版本不存在 agentId={}, version={}", agentId, version);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "图版本不存在: agentId=" + agentId + ", version=" + version);
        }
        agent.setPublishedGraphDefinition(history.getDefinition());
        agent.setPublishedVersion(version);
        agent.setGraphDefinition(history.getDefinition());
        saveAgentGraph(agent);
        log.info("Rolled back agent graph: agentId={}, version={}", agentId, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertFragment(Long agentId, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        AiAgent agent = requireAgent(agentId);
        Map<String, Object> def = parseDefinitionMap(agent.getGraphDefinition());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existingNodes = (List<Map<String, Object>>) def.computeIfAbsent(
                GraphDefinitionKeys.NODES, k -> new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existingEdges = (List<Map<String, Object>>) def.computeIfAbsent(
                GraphDefinitionKeys.EDGES, k -> new ArrayList<>());

        Map<String, String> idMap = new HashMap<>();
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                if (node == null) {
                    continue;
                }
                Map<String, Object> copy = new HashMap<>(node);
                String oldId = stringId(copy.get(GraphDefinitionKeys.ID));
                String newId = shortUuid();
                if (StringUtils.hasText(oldId)) {
                    idMap.put(oldId, newId);
                }
                copy.put(GraphDefinitionKeys.ID, newId);
                existingNodes.add(copy);
            }
        }
        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                if (edge == null) {
                    continue;
                }
                Map<String, Object> copy = new HashMap<>(edge);
                copy.put(GraphDefinitionKeys.ID, shortUuid());
                remapEdgeEndpoint(copy, GraphDefinitionKeys.SOURCE, GraphDefinitionKeys.SOURCE_NODE_ID, idMap);
                remapEdgeEndpoint(copy, GraphDefinitionKeys.TARGET, GraphDefinitionKeys.TARGET_NODE_ID, idMap);
                existingEdges.add(copy);
            }
        }

        agent.setGraphDefinition(toJsonString(def));
        ensureGraphMode(agent);
        saveAgentGraph(agent);
        log.info("Inserted graph fragment: agentId={}, nodes={}, edges={}",
                agentId, nodes != null ? nodes.size() : 0, edges != null ? edges.size() : 0);
    }

    /**
     * CHAT 模式自动切到 GRAPH（画布保存 / 插入片段时）。
     *
     * @param agent 智能体实体
     */
    private void ensureGraphMode(AiAgent agent) {
        if (agent.getOrchestrateMode() == null
                || agent.getOrchestrateMode() == OrchestrateModeEnum.CHAT.getCode()) {
            agent.setOrchestrateMode(OrchestrateModeEnum.GRAPH.getCode());
        }
    }

    /**
     * 乐观锁更新图字段并失效运行时缓存；冲突抛 CONFLICT。
     *
     * @param agent 已改好的实体
     */
    private void saveAgentGraph(AiAgent agent) {
        int rows = aiAgentMapper.updateById(agent);
        if (rows == 0) {
            log.warn("图定义乐观锁冲突: agentId={}", agent.getId());
            throw new BusinessException(CommonErrorCode.CONFLICT.getCode(), MSG_OPTIMISTIC_CONFLICT);
        }
        graphRuntime.invalidateCache(agent.getId());
    }

    /**
     * 按主键加载智能体。
     *
     * @param agentId 智能体主键
     * @return 实体
     * @throws BusinessException 不存在
     */
    private AiAgent requireAgent(Long agentId) {
        AiAgent agent = aiAgentMapper.selectById(agentId);
        if (agent == null) {
            log.warn("智能体不存在: agentId={}", agentId);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "智能体不存在: id=" + agentId);
        }
        return agent;
    }

    /**
     * 解析草稿 JSON 为可变 Map；空则返回含空 nodes/edges 的结构。
     *
     * @param json 草稿 JSON
     * @return 可变定义 Map
     */
    private Map<String, Object> parseDefinitionMap(String json) {
        if (!StringUtils.hasText(json)) {
            Map<String, Object> empty = new HashMap<>();
            empty.put(GraphDefinitionKeys.NODES, new ArrayList<>());
            empty.put(GraphDefinitionKeys.EDGES, new ArrayList<>());
            return empty;
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
            if (parsed == null) {
                parsed = new HashMap<>();
            }
            if (!(parsed.get(GraphDefinitionKeys.NODES) instanceof List)) {
                parsed.put(GraphDefinitionKeys.NODES, new ArrayList<>());
            }
            if (!(parsed.get(GraphDefinitionKeys.EDGES) instanceof List)) {
                parsed.put(GraphDefinitionKeys.EDGES, new ArrayList<>());
            }
            return parsed;
        } catch (Exception e) {
            log.warn("parseDefinitionMap failed: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "草稿图定义 JSON 非法");
        }
    }

    /**
     * 将边端点 id 按映射表替换（兼容 source / sourceNodeId）。
     *
     * @param edge  边 Map
     * @param keyA  字段名 A
     * @param keyB  字段名 B
     * @param idMap 旧 id → 新 id
     */
    private void remapEdgeEndpoint(Map<String, Object> edge, String keyA, String keyB, Map<String, String> idMap) {
        String a = stringId(edge.get(keyA));
        String b = stringId(edge.get(keyB));
        String old = StringUtils.hasText(a) ? a : b;
        if (!StringUtils.hasText(old)) {
            return;
        }
        String mapped = idMap.getOrDefault(old, old);
        if (edge.containsKey(keyA)) {
            edge.put(keyA, mapped);
        }
        if (edge.containsKey(keyB)) {
            edge.put(keyB, mapped);
        }
        if (!edge.containsKey(keyA) && !edge.containsKey(keyB)) {
            edge.put(keyA, mapped);
        }
    }

    /**
     * 对象转字符串 id。
     *
     * @param v 原始值
     * @return 字符串；空返回 null
     */
    private static String stringId(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 生成短 UUID（去掉连字符取前 8 位）。
     *
     * @return 短 id
     */
    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 对象序列化为 JSON 字符串。
     *
     * @param obj 待序列化对象
     * @return JSON；obj 为 null 时返回 null
     * @throws BusinessException 序列化失败
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            log.warn("toJsonString failed: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.INVALID_PARAM);
        }
    }
}
