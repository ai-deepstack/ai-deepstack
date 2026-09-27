package org.deepstack.ai.graph;

import java.util.List;

/**
 * 图扩展结果：邻居节点 + 路径上的边（供控制台连线与召回调试）。
 */
public final class GraphNeighborhood {

    private final List<GraphNode> nodes;
    private final List<GraphEdge> edges;

    public GraphNeighborhood(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        this.edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /** 返回 Nodes。 */
    public List<GraphNode> getNodes() {
        return nodes;
    }

    /** 返回 Edges。 */
    public List<GraphEdge> getEdges() {
        return edges;
    }
}
