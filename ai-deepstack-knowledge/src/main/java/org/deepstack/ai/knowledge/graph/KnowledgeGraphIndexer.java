package org.deepstack.ai.knowledge.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.deepstack.ai.graph.GraphEdge;
import org.deepstack.ai.graph.GraphNode;
import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.knowledge.DocGraphStatusEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.knowledge.mapper.KnowledgeChunkMapper;
import org.deepstack.ai.knowledge.mapper.KnowledgeDocumentMapper;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import org.deepstack.ai.knowledge.model.entity.KnowledgeDocument;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识库写图服务：文档向量化成功后写入 AGE（Document/Chunk/Entity + 边）。
 * <p>
 * 门闩：{@code graph.enabled} ∧ GraphStore ready ∧ {@code kb.enable_graph}。
 * 写图失败不回滚向量，仅更新 {@code graph_status}/{@code graph_error}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphIndexer {

    private static final int PREVIEW_MAX = 200;
    private static final int ERROR_MAX = 500;
    /** 文档级 ABOUT：提及次数 ≥ 该阈值的实体 */
    private static final int ABOUT_MIN_MENTIONS = 2;

    private final ObjectProvider<GraphStore> graphStoreProvider;
    private final SysConfigPort sysConfigPort;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeGraphExtractor extractor;

    /**
     * 当前知识库是否允许写图 / 召回扩边。
     */
    public boolean graphWritable(KnowledgeBase kb) {
        if (kb == null) {
            return false;
        }
        if (!sysConfigPort.isYes(SysConfigKeys.GRAPH_ENABLED)) {
            log.debug("graphWritable=false: 平台 graph.enabled 关闭, kb={}", kb.getBaseCode());
            return false;
        }
        if (!YesNo.isYes(kb.getEnableGraph())) {
            log.debug("graphWritable=false: 库未开 enable_graph, kb={}", kb.getBaseCode());
            return false;
        }
        GraphStore store = graphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("graphWritable=false: GraphStore 未装配, kb={}", kb.getBaseCode());
            return false;
        }
        if (!store.info().isReady()) {
            log.warn("graphWritable=false: GraphStore not ready, kb={}, detail={}",
                    kb.getBaseCode(), store.info().getDetail());
            return false;
        }
        return true;
    }

    /**
     * 为已向量化文档建图（幂等：先 deleteByDocument 再写入）。
     */
    public void indexDocument(KnowledgeDocument doc, KnowledgeBase kb) {
        if (doc == null || doc.getId() == null || kb == null) {
            log.warn("indexDocument 参数无效: doc={}, kb={}",
                    doc != null ? doc.getId() : null,
                    kb != null ? kb.getBaseCode() : null);
            return;
        }
        Long docId = doc.getId();
        log.info("indexDocument 开始: docId={}, kb={}, title={}",
                docId, kb.getBaseCode(), doc.getTitle());

        if (!graphWritable(kb)) {
            markStatus(doc, DocGraphStatusEnum.SKIPPED, null);
            log.info("indexDocument 跳过写图: docId={}, kb={}", docId, kb.getBaseCode());
            return;
        }

        GraphStore store = graphStoreProvider.getIfAvailable();
        String scope = KnowledgeGraphScopes.scopeOf(kb.getBaseCode());
        String documentIdStr = String.valueOf(docId);

        markStatus(doc, DocGraphStatusEnum.WRITING, null);
        try {
            // 重建：先清该文档挂载的 Doc/Chunk 节点及边
            log.info("写图前 deleteByDocument: scope={}, documentId={}", scope, documentIdStr);
            store.deleteByDocument(scope, documentIdStr);

            List<KnowledgeChunk> chunks = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunk>()
                            .eq(KnowledgeChunk::getDocumentId, docId)
                            .orderByAsc(KnowledgeChunk::getChunkIndex));
            log.info("写图加载 chunks: docId={}, count={}", docId, chunks.size());

            store.ensureScope(scope);

            // Document 节点
            Map<String, Object> docProps = new LinkedHashMap<>();
            docProps.put("documentId", documentIdStr);
            docProps.put("title", doc.getTitle() == null ? "" : doc.getTitle());
            docProps.put("baseCode", kb.getBaseCode());
            store.upsertNodes(scope, List.of(
                    new GraphNode(KnowledgeGraphScopes.docKey(docId), "Document", docProps)));

            // Chunk 节点 + CONTAINS
            List<GraphNode> chunkNodes = new ArrayList<>();
            List<GraphEdge> contains = new ArrayList<>();
            String docKey = KnowledgeGraphScopes.docKey(docId);
            for (KnowledgeChunk c : chunks) {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("chunkId", c.getId());
                props.put("documentId", documentIdStr);
                props.put("preview", preview(c.getContent()));
                chunkNodes.add(new GraphNode(
                        KnowledgeGraphScopes.chunkKey(c.getId()), "Chunk", props));
                contains.add(new GraphEdge(docKey,
                        KnowledgeGraphScopes.chunkKey(c.getId()), "CONTAINS", Map.of()));
            }
            if (!chunkNodes.isEmpty()) {
                store.upsertNodes(scope, chunkNodes);
                store.upsertEdges(scope, contains);
                log.info("已写入 Document/Chunk/CONTAINS: docId={}, chunks={}",
                        docId, chunkNodes.size());
            }

            // LLM 抽实体/关系
            KnowledgeGraphExtractResult extracted = extractor.extract(kb, chunks);
            writeEntitiesAndRelations(store, scope, docKey, chunks, extracted);

            markStatus(doc, DocGraphStatusEnum.WRITTEN, null);
            log.info("indexDocument 成功: docId={}, kb={}, entities={}, relations={}",
                    docId, kb.getBaseCode(),
                    extracted.getEntities().size(), extracted.getRelations().size());
        } catch (Exception e) {
            log.error("indexDocument 失败: docId={}, kb={}, err={}",
                    docId, kb.getBaseCode(), e.getMessage(), e);
            markStatus(doc, DocGraphStatusEnum.FAILED, truncate(e.getMessage(), ERROR_MAX));
        }
    }

    /**
     * 仅重跑写图（不重 embed）。公开给 API。
     */
    public void regraph(Long documentId) {
        log.info("regraph: documentId={}", documentId);
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "文档不存在: id=" + documentId);
        }
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKnowledgeBaseId());
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: id=" + doc.getKnowledgeBaseId());
        }
        indexDocument(doc, kb);
    }

    /**
     * 删除文档关联图数据（删文档 / reembed 前调用）。
     */
    public void deleteDocumentGraph(KnowledgeBase kb, Long documentId) {
        if (kb == null || documentId == null) {
            return;
        }
        GraphStore store = graphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("deleteDocumentGraph 跳过: GraphStore 未装配, docId={}", documentId);
            return;
        }
        if (!sysConfigPort.isYes(SysConfigKeys.GRAPH_ENABLED) || !store.info().isReady()) {
            log.debug("deleteDocumentGraph 跳过: 图谱未启用或未就绪, docId={}", documentId);
            return;
        }
        String scope = KnowledgeGraphScopes.scopeOf(kb.getBaseCode());
        try {
            log.info("deleteDocumentGraph: scope={}, documentId={}", scope, documentId);
            store.deleteByDocument(scope, String.valueOf(documentId));
            int orphans = store.deleteOrphanEntities(scope);
            log.info("deleteDocumentGraph 孤儿实体清理: scope={}, deleted={}", scope, orphans);
        } catch (Exception e) {
            log.warn("deleteDocumentGraph 失败: docId={}, err={}", documentId, e.getMessage());
        }
    }

    /** 把抽取结果写入图存储。 */
    private void writeEntitiesAndRelations(GraphStore store, String scope, String docKey,
                                           List<KnowledgeChunk> chunks,
                                           KnowledgeGraphExtractResult extracted) {
        if (extracted.getEntities().isEmpty() && extracted.getRelations().isEmpty()) {
            log.info("无实体/关系可写: scope={}, docKey={}", scope, docKey);
            return;
        }

        // name(lower) → entityKey
        Map<String, String> nameToKey = new LinkedHashMap<>();
        List<GraphNode> entityNodes = new ArrayList<>();
        for (KnowledgeGraphExtractResult.Entity e : extracted.getEntities()) {
            String name = e.name().trim();
            String nk = name.toLowerCase(Locale.ROOT);
            String key = KnowledgeGraphScopes.entityKey(name);
            nameToKey.putIfAbsent(nk, key);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", name);
            props.put("type", e.type() == null ? "Concept" : e.type());
            // 注意：Entity 不写 documentId，避免 deleteByDocument 误删共享实体
            entityNodes.add(new GraphNode(key, "Entity", props));
        }
        if (!entityNodes.isEmpty()) {
            store.upsertNodes(scope, entityNodes);
            log.info("已 upsert Entity 节点: count={}", entityNodes.size());
        }

        // 将实体均匀挂到各 chunk（MENTIONS）：按实体名在内容中出现优先，否则轮询
        Map<String, Integer> mentionCount = new HashMap<>();
        List<GraphEdge> mentionEdges = new ArrayList<>();
        int i = 0;
        for (KnowledgeGraphExtractResult.Entity e : extracted.getEntities()) {
            String nk = e.name().trim().toLowerCase(Locale.ROOT);
            String ek = nameToKey.get(nk);
            if (ek == null || chunks.isEmpty()) {
                continue;
            }
            KnowledgeChunk target = pickChunkForEntity(chunks, e.name(), i++);
            mentionEdges.add(new GraphEdge(
                    KnowledgeGraphScopes.chunkKey(target.getId()), ek, "MENTIONS", Map.of()));
            mentionCount.merge(ek, 1, Integer::sum);
        }
        if (!mentionEdges.isEmpty()) {
            store.upsertEdges(scope, mentionEdges);
            log.info("已写入 MENTIONS: count={}", mentionEdges.size());
        }

        // RELATED_TO（及模型给出的其它类型）
        List<GraphEdge> relEdges = new ArrayList<>();
        for (KnowledgeGraphExtractResult.Relation r : extracted.getRelations()) {
            String fk = nameToKey.get(r.from().trim().toLowerCase(Locale.ROOT));
            String tk = nameToKey.get(r.to().trim().toLowerCase(Locale.ROOT));
            if (fk == null || tk == null || fk.equals(tk)) {
                log.debug("跳过无效关系: from={}, to={}, type={}", r.from(), r.to(), r.type());
                continue;
            }
            String type = StringUtils.hasText(r.type()) ? r.type() : "RELATED_TO";
            try {
                relEdges.add(new GraphEdge(fk, tk, type, Map.of()));
            } catch (IllegalArgumentException ex) {
                log.warn("非法关系类型，改用 RELATED_TO: type={}", type);
                relEdges.add(new GraphEdge(fk, tk, "RELATED_TO", Map.of()));
            }
        }
        if (!relEdges.isEmpty()) {
            store.upsertEdges(scope, relEdges);
            log.info("已写入关系边: count={}", relEdges.size());
        }

        // ABOUT：高频实体挂到 Document
        List<GraphEdge> about = new ArrayList<>();
        for (Map.Entry<String, Integer> en : mentionCount.entrySet()) {
            if (en.getValue() >= ABOUT_MIN_MENTIONS) {
                about.add(new GraphEdge(docKey, en.getKey(), "ABOUT", Map.of()));
            }
        }
        // 若全部不足阈值，至少取提及最多的前 3 个
        if (about.isEmpty() && !mentionCount.isEmpty()) {
            mentionCount.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .forEach(en -> about.add(new GraphEdge(docKey, en.getKey(), "ABOUT", Map.of())));
        }
        if (!about.isEmpty()) {
            store.upsertEdges(scope, about);
            log.info("已写入 ABOUT: count={}", about.size());
        }
    }

    /** 为实体挑选关联分块。 */
    private static KnowledgeChunk pickChunkForEntity(List<KnowledgeChunk> chunks,
                                                    String entityName, int roundRobin) {
        String needle = entityName.toLowerCase(Locale.ROOT);
        for (KnowledgeChunk c : chunks) {
            if (c.getContent() != null && c.getContent().toLowerCase(Locale.ROOT).contains(needle)) {
                return c;
            }
        }
        return chunks.get(Math.floorMod(roundRobin, chunks.size()));
    }

    /** 回写文档图谱状态。 */
    private void markStatus(KnowledgeDocument doc, DocGraphStatusEnum status, String error) {
        doc.setGraphStatus(status.getCode());
        doc.setGraphError(error);
        knowledgeDocumentMapper.updateById(doc);
        log.debug("更新 graphStatus: docId={}, status={}, error={}",
                doc.getId(), status.getLabel(), error);
    }

    /** 截取内容预览。 */
    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String t = content.replaceAll("\\s+", " ").trim();
        return t.length() <= PREVIEW_MAX ? t : t.substring(0, PREVIEW_MAX) + "…";
    }

    /** 按最大长度截断文本。 */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
