package org.deepstack.ai.graph;

import java.util.List;

/**
 * 知识图谱存储 SPI。
 * <p>
 * 业务只依赖本接口；具体库（Apache AGE / Neo4j 等）由可选实现模块提供。
 * {@code scope} 用于多知识库/租户隔离（如 {@code kb:12} 或知识库编码）。
 * </p>
 */
public interface GraphStore {

    /**
     * 确保 scope 对应的图/命名空间可用（幂等）。
     *
     * @param scope 隔离键（如 {@code kb:12}）
     */
    void ensureScope(String scope);

    /**
     * 按 key 幂等写入节点。
     *
     * @param scope 隔离键
     * @param nodes 节点列表
     */
    void upsertNodes(String scope, List<GraphNode> nodes);

    /**
     * 按 from/to/type 幂等写入边（端点须已存在）。
     *
     * @param scope 隔离键
     * @param edges 边列表
     */
    void upsertEdges(String scope, List<GraphEdge> edges);

    /**
     * 删除某文档关联的节点及边（按节点属性 documentId）。
     *
     * @param scope      隔离键
     * @param documentId 文档 ID
     */
    void deleteByDocument(String scope, String documentId);

    /**
     * 删除整个 scope（图或命名空间）。
     *
     * @param scope 隔离键
     */
    void deleteScope(String scope);

    /**
     * 从起点多跳扩展，返回到达的节点（不含边）。
     *
     * @param query 扩展查询
     * @return 邻居节点集合（不含起点）
     */
    List<GraphNode> expand(GraphExpandQuery query);

    /**
     * 从起点多跳扩展，返回节点与路径上出现的边（控制台图谱页用）。
     *
     * @param query 扩展查询
     * @return 邻域（节点 + 边）；默认实现仅返回 {@link #expand} 节点、边为空
     */
    default GraphNeighborhood expandNeighborhood(GraphExpandQuery query) {
        return new GraphNeighborhood(expand(query), List.of());
    }

    /**
     * 按 documentId 属性列出该文档相关节点之间的直接边（控制台按文档过滤）。
     *
     * @param scope      隔离键
     * @param documentId 文档 ID 字符串
     * @return 边列表；默认空
     */
    default List<GraphEdge> listDocumentEdges(String scope, String documentId) {
        return List.of();
    }

    /**
     * 统计 scope 内节点/边约数（控制台 summary）。默认空统计。
     *
     * @param scope 隔离键
     * @return 计数；实现失败时应抛出，由调用方降级
     */
    default GraphScopeSummary summarize(String scope) {
        return GraphScopeSummary.empty();
    }

    /**
     * 删除已不再挂到文档/分片上的 Entity。
     * <p>
     * 无边节点，以及只剩实体之间 {@code RELATED_TO}、没有任何 {@code MENTIONS}/{@code ABOUT} 的节点，都算孤儿。
     * {@code DETACH DELETE} 会一并去掉它们之间的关系边。
     * </p>
     *
     * @param scope 隔离键
     * @return 删除条数；默认 0
     */
    default int deleteOrphanEntities(String scope) {
        return 0;
    }

    /**
     * 实现方与就绪状态。
     *
     * @return 提供方信息
     */
    GraphProviderInfo info();
}
