package org.deepstack.ai.graph.age.web;

import org.deepstack.ai.graph.GraphEdge;
import org.deepstack.ai.graph.GraphExpandQuery;
import org.deepstack.ai.graph.GraphNeighborhood;
import org.deepstack.ai.graph.GraphNode;
import org.deepstack.ai.graph.GraphProviderInfo;
import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.graph.age.dto.GraphEdgesRequest;
import org.deepstack.ai.graph.age.dto.GraphExpandRequest;
import org.deepstack.ai.graph.age.dto.GraphNodesRequest;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识图谱调试/管理 API（需装配 {@link GraphStore}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@ConditionalOnBean(GraphStore.class)
public class GraphController {

    private final GraphStore graphStore;

    /**
     * 查询图谱提供方状态（是否就绪等）。
     *
     * @return 提供方信息
     */
    @GetMapping("/info")
    public Response<GraphProviderInfo> info() {
        log.info("API graph info");
        return Response.success(graphStore.info());
    }

    /**
     * 确保指定 scope（图）存在。
     *
     * @param scope 图 scope 名称
     */
    @PostMapping("/scopes/{scope}/ensure")
    public Response<Void> ensure(@PathVariable("scope") String scope) {
        log.info("API graph ensureScope: scope={}", scope);
        graphStore.ensureScope(scope);
        return Response.success();
    }

    /**
     * 批量 upsert 节点。
     *
     * @param req 含 scope 与节点列表
     */
    @PostMapping("/nodes")
    public Response<Void> upsertNodes(@RequestBody GraphNodesRequest req) {
        log.info("API graph upsertNodes: scope={}, count={}",
                req != null ? req.getScope() : null,
                req != null && req.getNodes() != null ? req.getNodes().size() : 0);
        List<GraphNode> nodes = req.getNodes().stream()
                .map(n -> new GraphNode(n.getKey(), n.getLabel(), n.getProperties()))
                .toList();
        graphStore.upsertNodes(req.getScope(), nodes);
        return Response.success();
    }

    /**
     * 批量 upsert 边。
     *
     * @param req 含 scope 与边列表
     */
    @PostMapping("/edges")
    public Response<Void> upsertEdges(@RequestBody GraphEdgesRequest req) {
        log.info("API graph upsertEdges: scope={}, count={}",
                req != null ? req.getScope() : null,
                req != null && req.getEdges() != null ? req.getEdges().size() : 0);
        List<GraphEdge> edges = req.getEdges().stream()
                .map(e -> new GraphEdge(e.getFromKey(), e.getToKey(), e.getType(), e.getProperties()))
                .toList();
        graphStore.upsertEdges(req.getScope(), edges);
        return Response.success();
    }

    /**
     * 从起始节点按跳数扩展邻居（仅节点）。
     *
     * @param req 扩展请求（scope、startKey、hops、limit、edgeTypes）
     * @return 邻居节点列表
     */
    @PostMapping("/expand")
    public Response<List<GraphNode>> expand(@RequestBody GraphExpandRequest req) {
        log.info("API graph expand: scope={}, startKey={}",
                req != null ? req.getScope() : null,
                req != null ? req.getStartKey() : null);
        GraphExpandQuery query = toQuery(req);
        return Response.success(graphStore.expand(query));
    }

    /**
     * 从起始节点多跳扩展，返回邻居节点与路径上的边（控制台连线）。
     *
     * @param req 扩展请求
     * @return {@link org.deepstack.ai.graph.GraphNeighborhood}
     */
    @PostMapping("/expand/neighborhood")
    public Response<GraphNeighborhood> expandNeighborhood(
            @RequestBody GraphExpandRequest req) {
        log.info("API graph expandNeighborhood: scope={}, startKey={}, hops={}, limit={}",
                req != null ? req.getScope() : null,
                req != null ? req.getStartKey() : null,
                req != null ? req.getHops() : null,
                req != null ? req.getLimit() : null);
        GraphExpandQuery query = toQuery(req);
        return Response.success(graphStore.expandNeighborhood(query));
    }

    /** 请求 DTO 转为查询对象。 */
    private static GraphExpandQuery toQuery(GraphExpandRequest req) {
        return new GraphExpandQuery(
                req.getScope(),
                req.getStartKey(),
                req.getHops() == null ? 1 : req.getHops(),
                req.getLimit() == null ? 20 : req.getLimit(),
                req.getEdgeTypes()
        );
    }

    /**
     * 按 documentId 删除关联图数据。
     *
     * @param documentId 文档/记忆图 key
     * @param scope      图 scope
     */
    @DeleteMapping("/documents/{documentId}")
    public Response<Void> deleteByDocument(
            @PathVariable("documentId") String documentId,
            @RequestParam("scope") String scope) {
        log.info("API graph deleteByDocument: scope={}, documentId={}", scope, documentId);
        graphStore.deleteByDocument(scope, documentId);
        return Response.success();
    }

    /**
     * 删除整个 scope。
     *
     * @param scope 图 scope 名称
     */
    @DeleteMapping("/scopes/{scope}")
    public Response<Void> deleteScope(@PathVariable("scope") String scope) {
        log.info("API graph deleteScope: scope={}", scope);
        graphStore.deleteScope(scope);
        return Response.success();
    }
}
