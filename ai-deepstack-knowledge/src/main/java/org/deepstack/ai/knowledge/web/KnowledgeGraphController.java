package org.deepstack.ai.knowledge.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.deepstack.ai.graph.GraphEdge;
import org.deepstack.ai.graph.GraphExpandQuery;
import org.deepstack.ai.graph.GraphNeighborhood;
import org.deepstack.ai.graph.GraphNode;
import org.deepstack.ai.graph.GraphScopeSummary;
import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.knowledge.DocGraphStatusEnum;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.Response;
import org.deepstack.ai.knowledge.graph.KnowledgeGraphScopes;
import org.deepstack.ai.knowledge.mapper.KnowledgeBaseMapper;
import org.deepstack.ai.knowledge.mapper.KnowledgeChunkMapper;
import org.deepstack.ai.knowledge.mapper.KnowledgeDocumentMapper;
import org.deepstack.ai.knowledge.model.dto.response.KnowledgeGraphSummaryResponse;
import org.deepstack.ai.knowledge.model.dto.response.KnowledgeGraphViewResponse;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import org.deepstack.ai.knowledge.model.entity.KnowledgeDocument;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库图谱只读 API（scope 固定为 {@code kb:{baseCode}}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeGraphController {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ObjectProvider<GraphStore> graphStoreProvider;
    private final SysConfigPort sysConfigPort;

    /**
     * 图谱摘要：开关、就绪、节点/边约数、最近写图时间。
     */
    @GetMapping("/{baseCode}/graph/summary")
    public Response<KnowledgeGraphSummaryResponse> summary(@PathVariable("baseCode") String baseCode) {
        log.info("API kb graph summary: baseCode={}", baseCode);
        if (!StringUtils.hasText(baseCode)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "baseCode 不能为空");
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getBaseCode, baseCode.trim())
                        .last("LIMIT 1"));
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: baseCode=" + baseCode);
        }

        KnowledgeGraphSummaryResponse out = new KnowledgeGraphSummaryResponse();
        String scope = KnowledgeGraphScopes.scopeOf(kb.getBaseCode());
        out.setScope(scope);
        out.setEnableGraph(YesNo.isYes(kb.getEnableGraph()));
        boolean enabled = sysConfigPort.isYes(SysConfigKeys.GRAPH_ENABLED);
        out.setGraphEnabled(enabled);

        KnowledgeDocument latest = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, kb.getId())
                        .eq(KnowledgeDocument::getGraphStatus, DocGraphStatusEnum.WRITTEN.getCode())
                        .orderByDesc(KnowledgeDocument::getUpdateTime)
                        .last("LIMIT 1"));
        if (latest != null) {
            out.setLastGraphTime(latest.getUpdateTime());
        }

        if (!enabled) {
            out.setMessage("平台 graph.enabled 已关闭");
            log.info("kb graph summary: graph.enabled=0, baseCode={}", baseCode);
            return Response.success(out);
        }
        GraphStore store = graphStoreProvider.getIfAvailable();
        if (store == null || !store.info().isReady()) {
            String detail = store == null ? "GraphStore 未装配" : store.info().getDetail();
            out.setStoreReady(false);
            out.setMessage("图谱未就绪: " + detail);
            log.warn("kb graph summary 未就绪: baseCode={}, detail={}", baseCode, detail);
            return Response.success(out);
        }
        out.setStoreReady(true);
        try {
            GraphScopeSummary stats = store.summarize(scope);
            out.setNodeCount(stats.getNodeCount());
            out.setEdgeCount(stats.getEdgeCount());
            out.setDocumentCount(stats.getDocumentCount());
            out.setChunkCount(stats.getChunkCount());
            out.setEntityCount(stats.getEntityCount());
            log.info("kb graph summary 完成: baseCode={}, nodes={}, edges={}, last={}",
                    baseCode, stats.getNodeCount(), stats.getEdgeCount(), out.getLastGraphTime());
        } catch (Exception e) {
            log.error("kb graph summary 计数失败: baseCode={}, err={}", baseCode, e.getMessage(), e);
            out.setMessage("统计失败: " + e.getMessage());
        }
        return Response.success(out);
    }

    /**
     * 按知识库编码查看图谱邻域（节点 + 边）。
     * <p>
     * 起点优先级：{@code startKey} → {@code documentId}（doc_*）→ 库内首个 chunk（chk_*）。
     * 库未开 {@code enable_graph} 时仍允许查看已有图数据。
     * </p>
     *
     * @param baseCode   知识库编码
     * @param documentId 可选，按文档过滤起点
     * @param startKey   可选，显式起点（前端「展开邻居」用）
     * @param hops       跳数，默认 1
     * @param limit      节点上限，默认 80
     */
    @GetMapping("/{baseCode}/graph")
    public Response<KnowledgeGraphViewResponse> view(
            @PathVariable("baseCode") String baseCode,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "startKey", required = false) String startKeyParam,
            @RequestParam(value = "hops", required = false) Integer hops,
            @RequestParam(value = "limit", required = false) Integer limit) {
        log.info("API kb graph view: baseCode={}, documentId={}, startKey={}, hops={}, limit={}",
                baseCode, documentId, startKeyParam, hops, limit);

        KnowledgeGraphViewResponse out = new KnowledgeGraphViewResponse();
        if (!StringUtils.hasText(baseCode)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "baseCode 不能为空");
        }

        KnowledgeBase kb = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getBaseCode, baseCode.trim())
                        .last("LIMIT 1"));
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: baseCode=" + baseCode);
        }

        String scope = KnowledgeGraphScopes.scopeOf(kb.getBaseCode());
        out.setScope(scope);

        if (!sysConfigPort.isYes(SysConfigKeys.GRAPH_ENABLED)) {
            out.setMessage("平台 graph.enabled 已关闭，无法读取图谱");
            log.info("kb graph view 跳过: graph.enabled=0, baseCode={}", baseCode);
            return Response.success(out);
        }

        GraphStore store = graphStoreProvider.getIfAvailable();
        if (store == null || !store.info().isReady()) {
            String detail = store == null ? "GraphStore 未装配" : store.info().getDetail();
            out.setMessage("图谱未就绪: " + detail);
            log.warn("kb graph view 未就绪: baseCode={}, detail={}", baseCode, detail);
            return Response.success(out);
        }

        if (!YesNo.isYes(kb.getEnableGraph())) {
            log.info("kb graph view: 库未开 enable_graph，仍尝试读取已有数据, baseCode={}", baseCode);
        }

        int h = hops == null || hops < 1 ? 1 : Math.min(hops, 5);
        int lim = limit == null || limit < 1 ? 80 : Math.min(limit, 500);

        String startKey = resolveStartKey(kb, documentId, startKeyParam, out);
        if (!StringUtils.hasText(startKey)) {
            return Response.success(out);
        }
        out.setStartKey(startKey);

        try {
            // 按文档过滤时优先列出文档直接边，再叠加邻域扩展
            List<GraphEdge> docEdges = List.of();
            if (documentId != null) {
                docEdges = store.listDocumentEdges(scope, String.valueOf(documentId));
                log.debug("kb graph listDocumentEdges: baseCode={}, edges={}",
                        baseCode, docEdges.size());
            }

            GraphExpandQuery query = new GraphExpandQuery(scope, startKey, h, lim, List.of());
            GraphNeighborhood nb = store.expandNeighborhood(query);
            log.info("kb graph expandNeighborhood: baseCode={}, startKey={}, nodes={}, edges={}",
                    baseCode, startKey, nb.getNodes().size(), nb.getEdges().size());

            // 合并：起点本身不在 expand 邻居中，需补一个占位节点（若属性未知则仅 key）
            Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
            nodeMap.put(startKey, new GraphNode(startKey, inferLabel(startKey), Map.of()));
            for (GraphNode n : nb.getNodes()) {
                if (n != null && StringUtils.hasText(n.getKey())) {
                    nodeMap.put(n.getKey(), n);
                }
            }
            // 文档边上的端点也纳入节点集合
            Set<String> edgeKeys = new LinkedHashSet<>();
            List<GraphEdge> allEdges = new ArrayList<>();
            for (GraphEdge e : docEdges) {
                if (e == null) {
                    continue;
                }
                edgeKeys.add(e.getFromKey() + "->" + e.getType() + "->" + e.getToKey());
                allEdges.add(e);
                nodeMap.putIfAbsent(e.getFromKey(),
                        new GraphNode(e.getFromKey(), inferLabel(e.getFromKey()), Map.of()));
                nodeMap.putIfAbsent(e.getToKey(),
                        new GraphNode(e.getToKey(), inferLabel(e.getToKey()), Map.of()));
            }
            for (GraphEdge e : nb.getEdges()) {
                if (e == null) {
                    continue;
                }
                String ek = e.getFromKey() + "->" + e.getType() + "->" + e.getToKey();
                if (edgeKeys.add(ek)) {
                    allEdges.add(e);
                }
            }

            out.setNodes(new ArrayList<>(nodeMap.values()));
            out.setEdges(allEdges);
            if (out.getNodes().isEmpty()) {
                out.setMessage("起点附近暂无图数据，请确认文档已写图成功");
            }
            log.info("kb graph view 完成: baseCode={}, nodes={}, edges={}",
                    baseCode, out.getNodes().size(), out.getEdges().size());
        } catch (Exception e) {
            log.error("kb graph view 失败: baseCode={}, err={}", baseCode, e.getMessage(), e);
            out.setMessage("读取图谱失败: " + e.getMessage());
        }
        return Response.success(out);
    }

    /**
     * 解析扩展起点：startKey → documentId → 库内首个 chunk。
     */
    private String resolveStartKey(KnowledgeBase kb, Long documentId, String startKeyParam,
                                   KnowledgeGraphViewResponse out) {
        if (StringUtils.hasText(startKeyParam)) {
            String key = startKeyParam.trim();
            log.debug("graph startKey=explicit: key={}", key);
            return key;
        }
        if (documentId != null) {
            String key = KnowledgeGraphScopes.docKey(documentId);
            log.debug("graph startKey=doc: documentId={}", documentId);
            return key;
        }
        KnowledgeChunk first = knowledgeChunkMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getKnowledgeBaseId, kb.getId())
                        .orderByAsc(KnowledgeChunk::getId)
                        .last("LIMIT 1"));
        if (first == null || first.getId() == null) {
            out.setMessage("知识库暂无分片，无法引导图谱起点；请指定 documentId / startKey 或先完成向量化");
            log.info("kb graph 无 bootstrap chunk: baseCode={}", kb.getBaseCode());
            return null;
        }
        String key = KnowledgeGraphScopes.chunkKey(first.getId());
        log.info("kb graph bootstrap startKey=chunk: baseCode={}, chunkId={}",
                kb.getBaseCode(), first.getId());
        return key;
    }

    /** 由节点 key 推断标签。 */
    private static String inferLabel(String key) {
        if (key == null) {
            return "Node";
        }
        if (key.startsWith("doc_")) {
            return "Document";
        }
        if (key.startsWith("chk_")) {
            return "Chunk";
        }
        if (key.startsWith("ent_")) {
            return "Entity";
        }
        return "Node";
    }
}
