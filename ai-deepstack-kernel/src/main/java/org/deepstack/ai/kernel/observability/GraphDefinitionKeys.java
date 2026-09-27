package org.deepstack.ai.kernel.observability;

/**
 * 图定义 JSON 字段名（nodes / edges / 端点 id）。
 */
public final class GraphDefinitionKeys {

    /** 节点列表。 */
    public static final String NODES = "nodes";
    /** 边列表。 */
    public static final String EDGES = "edges";
    /** 节点或边 id。 */
    public static final String ID = "id";
    /** 边起点（简写）。 */
    public static final String SOURCE = "source";
    /** 边起点（画布字段）。 */
    public static final String SOURCE_NODE_ID = "sourceNodeId";
    /** 边终点（简写）。 */
    public static final String TARGET = "target";
    /** 边终点（画布字段）。 */
    public static final String TARGET_NODE_ID = "targetNodeId";
    /** GraphSpec.graphType：智能体图。 */
    public static final String GRAPH_TYPE_AGENT = "AGENT";

    private GraphDefinitionKeys() {
    }
}
