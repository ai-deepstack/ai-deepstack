package org.deepstack.ai.agent.graph;

import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.engine.GraphSpec;
import org.deepstack.ai.kernel.observability.GraphDefinitionKeys;

/**
 * AiAgent ↔ GraphSpec 转换（编排侧组装，runtime 只认 GraphSpec）。
 */
public final class AgentGraphSpecs {

    private AgentGraphSpecs() {
    }

    /**
     * 由草稿图定义组装 GraphSpec（试跑 / 画布）。
     *
     * @param agent 智能体实体
     * @return 图规格；agent 为 null 时返回 null
     */
    public static GraphSpec from(AiAgent agent) {
        if (agent == null) {
            return null;
        }
        return fill(agent, agent.getGraphDefinition(),
                agent.getGraphVersion() != null ? agent.getGraphVersion() : 1);
    }

    /**
     * 由已发布图定义组装 GraphSpec（正式对话 GRAPH）。
     *
     * @param agent 智能体实体
     * @return 图规格；agent 为 null 时返回 null
     */
    public static GraphSpec fromPublished(AiAgent agent) {
        if (agent == null) {
            return null;
        }
        return fill(agent, agent.getPublishedGraphDefinition(),
                agent.getPublishedVersion() != null ? agent.getPublishedVersion() : 1);
    }

    /**
     * 填充 GraphSpec 公共字段。
     *
     * @param agent      智能体
     * @param definition 图定义 JSON
     * @param version    版本号
     * @return 图规格
     */
    private static GraphSpec fill(AiAgent agent, String definition, int version) {
        GraphSpec spec = new GraphSpec();
        spec.setId(agent.getId());
        spec.setCode(agent.getAgentCode());
        spec.setName(agent.getAgentName());
        spec.setDefinition(definition);
        spec.setVersion(version);
        spec.setEnabled(agent.getEnabled());
        spec.setGraphType(GraphDefinitionKeys.GRAPH_TYPE_AGENT);
        return spec;
    }
}
