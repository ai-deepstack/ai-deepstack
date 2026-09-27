package org.deepstack.ai.knowledge.service.impl;


import org.deepstack.ai.graph.GraphExpandQuery;
import org.deepstack.ai.graph.GraphNode;
import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.knowledge.graph.KnowledgeGraphIndexer;
import org.deepstack.ai.knowledge.graph.KnowledgeGraphScopes;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeChunkPageRequest;
import org.deepstack.ai.infra.llm.EmbeddingClientFactory;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import org.deepstack.ai.knowledge.mapper.KnowledgeChunkMapper;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import org.deepstack.ai.knowledge.service.KnowledgeChunkService;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.knowledge.support.RecallQueryTimeout;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 知识库分片服务实现
 * <p>
 * 混合召回：
 * <ol>
 *   <li>阶段 A：全文与 embedding 并行，再向量 SQL，RRF 融合得到 seeds；</li>
 *   <li>阶段 B：若库开启图谱且 GraphStore 就绪，对 seeds 并发 expand 补召回。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
public class KnowledgeChunkServiceImpl extends ServiceImpl<KnowledgeChunkMapper, KnowledgeChunk>
        implements KnowledgeChunkService {

    /** RRF 融合常数（业界默认 60） */
    private static final int RRF_K = 60;

    private static final List<String> GRAPH_EDGE_TYPES =
            List.of("MENTIONS", "RELATED_TO", "CONTAINS", "ABOUT");

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingClientFactory embeddingClientFactory;
    private final ObjectProvider<GraphStore> graphStoreProvider;
    private final SysConfigPort sysConfigPort;
    private final KnowledgeGraphIndexer knowledgeGraphIndexer;
    private final Executor knowledgeRecallExecutor;
    /** embedding HTTP，与召回池隔离，避免超时后仍占着召回线程 */
    private final Executor knowledgeEmbedExecutor;

    public KnowledgeChunkServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                     EmbeddingClientFactory embeddingClientFactory,
                                     ObjectProvider<GraphStore> graphStoreProvider,
                                     SysConfigPort sysConfigPort,
                                     KnowledgeGraphIndexer knowledgeGraphIndexer,
                                     @Qualifier("knowledgeRecallExecutor") Executor knowledgeRecallExecutor,
                                     @Qualifier("knowledgeEmbedExecutor") Executor knowledgeEmbedExecutor) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingClientFactory = embeddingClientFactory;
        this.graphStoreProvider = graphStoreProvider;
        this.sysConfigPort = sysConfigPort;
        this.knowledgeGraphIndexer = knowledgeGraphIndexer;
        this.knowledgeRecallExecutor = knowledgeRecallExecutor;
        this.knowledgeEmbedExecutor = knowledgeEmbedExecutor;
    }

    // ===== 检索方法 =====

    /** 按向量相似度检索分块。 */
    @Override
    public List<KnowledgeChunk> searchByVector(Long knowledgeBaseId, String queryText, int topK) {
        log.info("searchByVector: kbId={}, topK={}, queryLen={}",
                knowledgeBaseId, topK, queryText != null ? queryText.length() : 0);
        if (!StringUtils.hasText(queryText)) {
            log.warn("searchByVector 跳过: queryText 为空, kbId={}", knowledgeBaseId);
            return Collections.emptyList();
        }
        KnowledgeBase kb = knowledgeBaseService.getEnabledById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在或已禁用: id=" + knowledgeBaseId);
        }

        String vector = embedToVectorString(kb.getEmbeddingModelCode(), queryText);
        List<KnowledgeChunk> results = baseMapper.searchByVector(knowledgeBaseId, vector, topK);
        log.info("searchByVector 完成: kbId={}, hit={}", knowledgeBaseId, results.size());
        return results;
    }

    /** 按全文检索分块。 */
    @Override
    public List<KnowledgeChunk> searchByFullText(Long knowledgeBaseId, String queryText, int topK) {
        log.info("searchByFullText: kbId={}, topK={}, queryLen={}",
                knowledgeBaseId, topK, queryText != null ? queryText.length() : 0);
        if (!StringUtils.hasText(queryText)) {
            log.warn("searchByFullText 跳过: queryText 为空, kbId={}", knowledgeBaseId);
            return Collections.emptyList();
        }
        List<KnowledgeChunk> results = baseMapper.searchByFullText(knowledgeBaseId, queryText, topK);
        log.info("searchByFullText 完成: kbId={}, hit={}", knowledgeBaseId, results.size());
        return results;
    }

    /**
     * 混合检索：阶段 A 向量∥全文 RRF；阶段 B 可选图谱扩边。
     */
    @Override
    public List<KnowledgeChunk> hybridSearch(Long knowledgeBaseId, String queryText,
                                             int topK, Double similarityThreshold) {
        log.info("hybridSearch: kbId={}, topK={}, threshold={}, queryLen={}",
                knowledgeBaseId, topK, similarityThreshold,
                queryText != null ? queryText.length() : 0);
        if (!StringUtils.hasText(queryText)) {
            log.warn("hybridSearch 跳过: queryText 为空, kbId={}", knowledgeBaseId);
            return Collections.emptyList();
        }
        KnowledgeBase kb = knowledgeBaseService.getEnabledById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在或已禁用: id=" + knowledgeBaseId);
        }

        int recallSize = Math.max(topK * 3, 10);
        long t0 = System.currentTimeMillis();
        long timeoutMs = Math.max(500L, sysConfigPort.getInt(
                SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, 3000));
        int timeoutSec = (int) Math.max(1L, (timeoutMs + 999) / 1000);
        // 外层多等 1s，让语句超时先把 SQL 取消并让任务正常结束
        long awaitMs = timeoutMs + 1000;

        // ----- 阶段 A：全文立即异步；embedding 与全文并行，向量 SQL 带语句超时 -----
        log.info("hybridSearch 阶段A 启动: kbId={}, recallSize={}, timeoutMs={}, sqlTimeoutSec={}",
                knowledgeBaseId, recallSize, timeoutMs, timeoutSec);
        CompletableFuture<List<KnowledgeChunk>> textFuture = CompletableFuture.supplyAsync(() -> {
            RecallQueryTimeout.setSeconds(timeoutSec);
            try {
                List<KnowledgeChunk> hits = baseMapper.searchByFullText(
                        knowledgeBaseId, queryText, recallSize);
                log.info("hybridSearch 全文完成: kbId={}, hit={}", knowledgeBaseId, hits.size());
                return hits;
            } catch (Exception e) {
                log.warn("hybridSearch 全文失败或超时，按空结果: kbId={}, err={}",
                        knowledgeBaseId, e.getMessage());
                return List.of();
            } finally {
                RecallQueryTimeout.clear();
            }
        }, knowledgeRecallExecutor);

        CompletableFuture<List<KnowledgeChunk>> vectorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String vector = embedToVectorString(kb.getEmbeddingModelCode(), queryText, timeoutMs);
                log.debug("hybridSearch embedding 完成: kbId={}, vectorLen={}",
                        knowledgeBaseId, vector.length());
                RecallQueryTimeout.setSeconds(timeoutSec);
                try {
                    List<KnowledgeChunk> hits = baseMapper.searchByVectorWithThreshold(
                            knowledgeBaseId, vector, recallSize, similarityThreshold);
                    log.info("hybridSearch 向量完成: kbId={}, hit={}", knowledgeBaseId, hits.size());
                    return hits;
                } finally {
                    RecallQueryTimeout.clear();
                }
            } catch (Exception e) {
                log.warn("hybridSearch 向量失败或超时，按空结果: kbId={}, err={}",
                        knowledgeBaseId, e.getMessage());
                return List.of();
            }
        }, knowledgeRecallExecutor);

        List<KnowledgeChunk> vectorResults = awaitStage("向量", knowledgeBaseId, vectorFuture, awaitMs);
        List<KnowledgeChunk> textResults = awaitStage("全文", knowledgeBaseId, textFuture, awaitMs);
        log.info("hybridSearch 阶段A join: kbId={}, vectorHit={}, textHit={}, costMs={}",
                knowledgeBaseId, vectorResults.size(), textResults.size(),
                System.currentTimeMillis() - t0);

        Map<Long, Double> scores = new HashMap<>();
        Map<Long, KnowledgeChunk> chunkMap = new HashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            KnowledgeChunk c = vectorResults.get(i);
            chunkMap.putIfAbsent(c.getId(), c);
            scores.merge(c.getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < textResults.size(); i++) {
            KnowledgeChunk c = textResults.get(i);
            chunkMap.putIfAbsent(c.getId(), c);
            scores.merge(c.getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        List<Long> seedIds = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(topK)
                .toList();
        List<KnowledgeChunk> seeds = new ArrayList<>(seedIds.size());
        for (Long id : seedIds) {
            seeds.add(chunkMap.get(id));
        }
        log.info("hybridSearch RRF seeds: kbId={}, seedCount={}", knowledgeBaseId, seeds.size());

        // ----- 阶段 B：图谱扩边 -----
        if (!knowledgeGraphIndexer.graphWritable(kb)) {
            log.info("hybridSearch 跳过阶段B（图谱不可写）: kb={}", kb.getBaseCode());
            return seeds;
        }
        try {
            return mergeGraphExpand(kb, seeds, scores, chunkMap, topK);
        } catch (Exception e) {
            log.warn("hybridSearch 阶段B 失败，回退 seeds: kb={}, err={}",
                    kb.getBaseCode(), e.getMessage());
            return seeds;
        }
    }

    /**
     * 对 seeds 并发 expand，收集邻居 chunkId，降权并入后截断。
     */
    private List<KnowledgeChunk> mergeGraphExpand(KnowledgeBase kb,
                                                  List<KnowledgeChunk> seeds,
                                                  Map<Long, Double> scores,
                                                  Map<Long, KnowledgeChunk> chunkMap,
                                                  int topK) {
        GraphStore store = graphStoreProvider.getIfAvailable();
        if (store == null || seeds.isEmpty()) {
            return seeds;
        }
        String scope = KnowledgeGraphScopes.scopeOf(kb.getBaseCode());
        int hops = Math.max(1, sysConfigPort.getInt(SysConfigKeys.KNOWLEDGE_GRAPH_HOPS, 2));
        int expandConcurrency = Math.max(1, sysConfigPort.getInt(
                SysConfigKeys.KNOWLEDGE_RECALL_MAX_EXPAND_CONCURRENCY, 8));
        long timeoutMs = Math.max(500L, sysConfigPort.getInt(
                SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, 3000));
        int graphExtra = resolveGraphExtra(topK);
        int expandLimit = Math.max(20, topK * 4);

        log.info("hybridSearch 阶段B: kb={}, seeds={}, hops={}, concurrency={}, timeoutMs={}, graphExtra={}",
                kb.getBaseCode(), seeds.size(), hops, expandConcurrency, timeoutMs, graphExtra);

        Set<Long> seedIdSet = new HashSet<>();
        for (KnowledgeChunk s : seeds) {
            seedIdSet.add(s.getId());
        }

        // neighborChunkId → 最佳 graphScore（0.7/(1+hop)）；多线程写入
        Map<Long, Double> graphScores = new ConcurrentHashMap<>();
        Semaphore lim = new Semaphore(Math.min(expandConcurrency, seeds.size()));
        List<CompletableFuture<Void>> futures = new ArrayList<>(seeds.size());

        for (KnowledgeChunk seed : seeds) {
            futures.add(CompletableFuture.runAsync(() -> {
                lim.acquireUninterruptibly();
                try {
                    String startKey = KnowledgeGraphScopes.chunkKey(seed.getId());
                    GraphExpandQuery q = new GraphExpandQuery(
                            scope, startKey, hops, expandLimit, GRAPH_EDGE_TYPES);
                    log.debug("expand seed: kb={}, chunkId={}, startKey={}",
                            kb.getBaseCode(), seed.getId(), startKey);
                    List<GraphNode> neighbors = store.expand(q);
                    for (GraphNode n : neighbors) {
                        Long chunkId = extractChunkId(n);
                        if (chunkId == null || seedIdSet.contains(chunkId)) {
                            continue;
                        }
                        // graphScore = 0.7/(1+hop)；无 hop 属性时按配置 hops 保守降权
                        int hop = extractHop(n, hops);
                        double gScore = 0.7 / (1.0 + hop);
                        graphScores.merge(chunkId, gScore, Math::max);
                    }
                    log.debug("expand seed 完成: chunkId={}, neighbors={}, scoredChunks={}",
                            seed.getId(), neighbors.size(), graphScores.size());
                } catch (Exception e) {
                    log.warn("expand 单种子失败，跳过: chunkId={}, err={}",
                            seed.getId(), e.getMessage());
                } finally {
                    lim.release();
                }
            }, knowledgeRecallExecutor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("hybridSearch 阶段B 超时，取消未完成 expand: kb={}, timeoutMs={}",
                    kb.getBaseCode(), timeoutMs);
            for (CompletableFuture<Void> f : futures) {
                f.cancel(true);
            }
        } catch (Exception e) {
            log.warn("hybridSearch 阶段B allOf 异常: kb={}, err={}", kb.getBaseCode(), e.getMessage());
            for (CompletableFuture<Void> f : futures) {
                f.cancel(true);
            }
        }

        if (graphScores.isEmpty()) {
            log.info("hybridSearch 阶段B 无新 chunk: kb={}", kb.getBaseCode());
            return seeds;
        }

        List<Long> extraIds = new ArrayList<>(graphScores.keySet());
        List<KnowledgeChunk> extras = baseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .in(KnowledgeChunk::getId, extraIds)
                        .eq(KnowledgeChunk::getKnowledgeBaseId, kb.getId()));
        log.info("hybridSearch 阶段B 回表: kb={}, requested={}, loaded={}",
                kb.getBaseCode(), extraIds.size(), extras.size());

        for (KnowledgeChunk c : extras) {
            chunkMap.putIfAbsent(c.getId(), c);
            Double gs = graphScores.get(c.getId());
            if (gs != null) {
                scores.merge(c.getId(), gs, Math::max);
            }
        }

        int finalLimit = topK + graphExtra;
        List<Long> ordered = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(finalLimit)
                .toList();
        List<KnowledgeChunk> result = new ArrayList<>(ordered.size());
        for (Long id : ordered) {
            KnowledgeChunk c = chunkMap.get(id);
            if (c != null) {
                result.add(c);
            }
        }
        log.info("hybridSearch 完成(含图): kb={}, seeds={}, graphExtras={}, final={}",
                kb.getBaseCode(), seeds.size(), extras.size(), result.size());
        return result;
    }

    /** 计算图谱扩展召回条数。 */
    private int resolveGraphExtra(int topK) {
        String raw = sysConfigPort.getString(SysConfigKeys.KNOWLEDGE_GRAPH_EXTRA_TOP_K);
        if (!StringUtils.hasText(raw)) {
            return topK;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            log.warn("非法 KNOWLEDGE_GRAPH_EXTRA_TOP_K={}, 回落 topK", raw);
            return topK;
        }
    }

    /** 从图节点解析分块 id。 */
    private static Long extractChunkId(GraphNode node) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        Object v = node.getProperties().get("chunkId");
        if (v == null) {
            // 兼容 key = chk_{id}
            String key = node.getKey();
            if (key != null && key.startsWith("chk_")) {
                try {
                    return Long.parseLong(key.substring(4));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从节点属性读 hop；缺失时用配置的 maxHops 作保守上界（分数更低）。
     */
    private static int extractHop(GraphNode node, int maxHops) {
        if (node == null || node.getProperties() == null) {
            return Math.max(1, maxHops);
        }
        Object v = node.getProperties().get("hop");
        if (v instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        if (v != null) {
            try {
                return Math.max(1, Integer.parseInt(String.valueOf(v).trim()));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Math.max(1, maxHops);
    }

    // ===== CRUD =====

    /** 分页查询。 */
    @Override
    public IPage<KnowledgeChunk> page(KnowledgeChunkPageRequest req) {
        log.info("page: documentId={}, pageNum={}, pageSize={}, keyword={}",
                req.getDocumentId(), req.getPageNum(), req.getPageSize(), req.getKeyword());
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, req.getDocumentId())
                .select(KnowledgeChunk.class, info -> !"embedding".equals(info.getProperty()))
                .orderByAsc(KnowledgeChunk::getChunkIndex);

        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.like(KnowledgeChunk::getContent, req.getKeyword());
        }

        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /** 按文档删除分块。 */
    @Override
    public int deleteByDocumentId(Long documentId) {
        log.info("deleteByDocumentId: documentId={}", documentId);
        int deleted = baseMapper.deleteByDocumentId(documentId);
        log.info("Deleted chunks by documentId: docId={}, count={}", documentId, deleted);
        return deleted;
    }

    // ===== private =====

    /**
     * 等待阶段 A 的一路结果。超时则取消任务并按空列表继续，不让 join 一直堵住召回。
     */
    private List<KnowledgeChunk> awaitStage(String lane, Long knowledgeBaseId,
                                            CompletableFuture<List<KnowledgeChunk>> future,
                                            long awaitMs) {
        try {
            List<KnowledgeChunk> hits = future.get(awaitMs, TimeUnit.MILLISECONDS);
            return hits != null ? hits : List.of();
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("hybridSearch 阶段A {} 等待超时，按空结果: kbId={}, awaitMs={}",
                    lane, knowledgeBaseId, awaitMs);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.warn("hybridSearch 阶段A {} 被中断，按空结果: kbId={}", lane, knowledgeBaseId);
            return List.of();
        } catch (ExecutionException e) {
            log.warn("hybridSearch 阶段A {} 异常，按空结果: kbId={}, err={}",
                    lane, knowledgeBaseId, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return List.of();
        }
    }

    /** 把文本向量化为 pgvector 字面量。 */
    private String embedToVectorString(String embeddingModelCode, String text) {
        return embedToVectorString(embeddingModelCode, text, 0);
    }

    /**
     * 查询向量。{@code timeoutMs > 0} 时把 HTTP 调用丢到 embedding 池并限时等待，
     * 避免召回线程被模型接口拖死。
     */
    private String embedToVectorString(String embeddingModelCode, String text, long timeoutMs) {
        if (!StringUtils.hasText(embeddingModelCode)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "embeddingModelCode 不能为空");
        }
        if (timeoutMs <= 0) {
            return callEmbedding(embeddingModelCode, text);
        }
        CompletableFuture<String> embedFuture;
        try {
            embedFuture = CompletableFuture.supplyAsync(
                    () -> callEmbedding(embeddingModelCode, text), knowledgeEmbedExecutor);
        } catch (RejectedExecutionException e) {
            log.warn("embedding 池已满，向量路按失败: modelCode={}, err={}",
                    embeddingModelCode, e.getMessage());
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(), "embedding 池已满");
        }
        try {
            return embedFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            embedFuture.cancel(true);
            log.warn("embedding 超时: modelCode={}, timeoutMs={}", embeddingModelCode, timeoutMs);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(),
                    "embedding 超时 timeoutMs=" + timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            embedFuture.cancel(true);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(), "embedding 被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException be) {
                throw be;
            }
            log.warn("embedding 失败: modelCode={}, err={}", embeddingModelCode, cause.getMessage());
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(),
                    "embedding 失败: " + cause.getMessage());
        }
    }

    /** 调用 Embedding 模型。 */
    private String callEmbedding(String embeddingModelCode, String text) {
        OpenAiEmbeddingModel model = embeddingClientFactory.getEmbeddingModel(embeddingModelCode);
        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(text), null));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            log.error("embedding 调用失败：返回空结果, modelCode={}", embeddingModelCode);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(),
                    "embedding 调用失败：返回空结果");
        }
        float[] embedding = response.getResults().get(0).getOutput();
        return floatArrayToVectorString(embedding);
    }

    /** 浮点数组转为向量字面量。 */
    private String floatArrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
