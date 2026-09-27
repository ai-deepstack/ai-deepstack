package org.deepstack.ai.memory.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.deepstack.ai.graph.GraphEdge;
import org.deepstack.ai.graph.GraphExpandQuery;
import org.deepstack.ai.graph.GraphNode;
import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.memory.LongTermMemoryStore;
import org.deepstack.ai.memory.MemoryItem;
import org.deepstack.ai.memory.MemoryQuery;
import org.deepstack.ai.memory.MemoryWrite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PG 主存 + 可选 {@link GraphStore}（AGE）关系增强。
 */
@Slf4j
public class PgHybridLongTermMemoryStore implements LongTermMemoryStore {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final int DEFAULT_EXPAND_CONCURRENCY = 4;
    private static final long DEFAULT_EXPAND_TIMEOUT_MS = 3000L;

    private final AiLongTermMemoryMapper mapper;
    private final ObjectProvider<GraphStore> graphStoreProvider;
    private final SysConfigPort sysConfigPort;
    private final Executor memoryRecallExecutor;

    public PgHybridLongTermMemoryStore(AiLongTermMemoryMapper mapper,
                                       ObjectProvider<GraphStore> graphStoreProvider,
                                       SysConfigPort sysConfigPort,
                                       Executor memoryRecallExecutor) {
        this.mapper = mapper;
        this.graphStoreProvider = graphStoreProvider;
        this.sysConfigPort = sysConfigPort;
        this.memoryRecallExecutor = memoryRecallExecutor;
    }

    /** 写入一条长期记忆。 */
    @Override
    public String remember(MemoryWrite write) {
        if (!sysConfigPort.isYes(SysConfigKeys.MEMORY_ENABLED)) {
            throw new IllegalStateException("memory.enabled=0, cannot write long-term memory");
        }
        if (!StringUtils.hasText(write.userId()) || !StringUtils.hasText(write.content())) {
            throw new IllegalArgumentException("userId and content required");
        }
        String agentCode = StringUtils.hasText(write.agentCode()) ? write.agentCode() : "*";
        AiLongTermMemory row = new AiLongTermMemory();
        row.setUserId(write.userId());
        row.setAgentCode(agentCode);
        row.setCategory(write.category());
        row.setContent(write.content().trim());
        row.setConversationId(write.conversationId());
        try {
            row.setEntitiesJson(JSON.toJSONString(write.entities()));
        } catch (Exception e) {
            row.setEntitiesJson("[]");
        }
        mapper.insert(row);
        String memoryId = String.valueOf(row.getId());
        String graphKey = "mem_" + memoryId;
        row.setGraphKey(graphKey);
        mapper.updateById(row);

        boolean useGraph = write.enableGraph() || sysConfigPort.isYes(SysConfigKeys.MEMORY_GRAPH_BY_DEFAULT);
        if (useGraph) {
            syncGraphOnWrite(write.userId(), agentCode, graphKey, row, write.entities());
        }
        log.info("LTM remember: id={}, userId={}, agentCode={}, graph={}",
                memoryId, write.userId(), agentCode, useGraph);
        return memoryId;
    }

    /** 按用户与查询召回长期记忆。 */
    @Override
    public List<MemoryItem> recall(MemoryQuery query) {
        if (!sysConfigPort.isYes(SysConfigKeys.MEMORY_ENABLED) || !StringUtils.hasText(query.userId())) {
            return List.of();
        }
        List<AiLongTermMemory> rows = mapper.search(
                query.userId(),
                query.agentCode(),
                query.queryText().trim(),
                query.limit()
        );
        LinkedHashMap<String, MemoryItem> byId = new LinkedHashMap<>();
        for (AiLongTermMemory row : rows) {
            byId.put(String.valueOf(row.getId()), toItem(row, 1.0));
        }

        boolean useGraph = query.enableGraph() || sysConfigPort.isYes(SysConfigKeys.MEMORY_GRAPH_BY_DEFAULT);
        GraphStore graph = graphStoreProvider.getIfAvailable();
        if (useGraph && graph != null && graph.info().isReady() && !rows.isEmpty()) {
            expandGraphNeighbors(query, rows, byId, graph);
        }

        List<MemoryItem> out = new ArrayList<>(byId.values());
        if (out.size() > query.limit()) {
            return out.subList(0, query.limit());
        }
        return out;
    }

    /**
     * 多种子 expand 限流并发；邻居 memoryId 批量回表。
     */
    private void expandGraphNeighbors(MemoryQuery query, List<AiLongTermMemory> rows,
                                      LinkedHashMap<String, MemoryItem> byId, GraphStore graph) {
        String scope = memoryScope(query.userId(), query.agentCode());
        Set<String> graphKeys = new LinkedHashSet<>();
        for (AiLongTermMemory row : rows) {
            if (StringUtils.hasText(row.getGraphKey())) {
                graphKeys.add(row.getGraphKey());
            }
        }
        if (graphKeys.isEmpty()) {
            return;
        }

        int concurrency = Math.max(1, Math.min(graphKeys.size(),
                sysConfigPort.getInt(SysConfigKeys.MEMORY_RECALL_MAX_EXPAND_CONCURRENCY,
                        DEFAULT_EXPAND_CONCURRENCY)));
        long timeoutMs = Math.max(500L, sysConfigPort.getInt(
                SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, (int) DEFAULT_EXPAND_TIMEOUT_MS));
        log.info("LTM graph expand 启动: scope={}, seeds={}, concurrency={}, timeoutMs={}",
                scope, graphKeys.size(), concurrency, timeoutMs);

        Semaphore lim = new Semaphore(concurrency);
        Set<Long> neighborIds = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> futures = new ArrayList<>(graphKeys.size());
        for (String gk : graphKeys) {
            futures.add(CompletableFuture.runAsync(() -> {
                lim.acquireUninterruptibly();
                try {
                    List<GraphNode> neighbors = graph.expand(new GraphExpandQuery(
                            scope, gk, 2, query.limit(), List.of("HAS_MEMORY", "ABOUT", "RELATED_TO")));
                    for (GraphNode n : neighbors) {
                        if (n == null || !"Memory".equals(n.getLabel()) || n.getProperties() == null) {
                            continue;
                        }
                        Object mid = n.getProperties().get("memoryId");
                        if (mid == null) {
                            continue;
                        }
                        String id = String.valueOf(mid);
                        if (byId.containsKey(id)) {
                            continue;
                        }
                        try {
                            neighborIds.add(Long.parseLong(id));
                        } catch (NumberFormatException ignored) {
                            // skip
                        }
                    }
                } catch (Exception e) {
                    log.warn("LTM graph expand failed: key={}, err={}", gk, e.getMessage());
                } finally {
                    lim.release();
                }
            }, memoryRecallExecutor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("LTM graph expand 总超时: scope={}, timeoutMs={}", scope, timeoutMs);
            for (CompletableFuture<Void> f : futures) {
                f.cancel(true);
            }
        } catch (Exception e) {
            log.warn("LTM graph expand allOf 异常: scope={}, err={}", scope, e.getMessage());
            for (CompletableFuture<Void> f : futures) {
                f.cancel(true);
            }
        }

        if (neighborIds.isEmpty()) {
            log.info("LTM graph expand 无新邻居: scope={}", scope);
            return;
        }
        List<AiLongTermMemory> extras = mapper.selectBatchIds(neighborIds);
        int added = 0;
        for (AiLongTermMemory extra : extras) {
            if (extra == null || extra.getId() == null) {
                continue;
            }
            String id = String.valueOf(extra.getId());
            if (byId.putIfAbsent(id, toItem(extra, 0.7)) == null) {
                added++;
            }
        }
        log.info("LTM graph expand 完成: scope={}, neighborIds={}, added={}",
                scope, neighborIds.size(), added);
    }

    /** 删除指定记忆。 */
    @Override
    public void forget(String userId, String memoryId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(memoryId)) {
            return;
        }
        AiLongTermMemory row = mapper.selectById(Long.parseLong(memoryId));
        if (row == null || !userId.equals(row.getUserId())) {
            return;
        }
        mapper.deleteById(row.getId());
        GraphStore graph = graphStoreProvider.getIfAvailable();
        if (graph != null && StringUtils.hasText(row.getGraphKey())) {
            try {
                String scope = memoryScope(row.getUserId(), row.getAgentCode());
                graph.deleteByDocument(scope, row.getGraphKey());
            } catch (Exception e) {
                log.warn("LTM graph delete failed: {}", e.getMessage());
            }
        }
    }

    /** 清空用户在指定智能体下的记忆。 */
    @Override
    public void clearUser(String userId, String agentCode) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        LambdaQueryWrapper<AiLongTermMemory> q = new LambdaQueryWrapper<AiLongTermMemory>()
                .eq(AiLongTermMemory::getUserId, userId)
                .eq(StringUtils.hasText(agentCode), AiLongTermMemory::getAgentCode, agentCode);
        List<AiLongTermMemory> rows = mapper.selectList(q);
        for (AiLongTermMemory row : rows) {
            mapper.deleteById(row.getId());
        }
        GraphStore graph = graphStoreProvider.getIfAvailable();
        if (graph != null) {
            try {
                graph.deleteScope(memoryScope(userId, agentCode));
            } catch (Exception e) {
                log.warn("LTM clear graph scope failed: {}", e.getMessage());
            }
        }
    }

    /** 写入记忆后同步知识图谱。 */
    private void syncGraphOnWrite(String userId, String agentCode, String graphKey,
                                  AiLongTermMemory row, List<String> entities) {
        GraphStore graph = graphStoreProvider.getIfAvailable();
        if (graph == null) {
            log.debug("GraphStore unavailable, skip memory graph sync");
            return;
        }
        if (!graph.info().isReady()) {
            log.warn("GraphStore not ready, skip memory graph sync: {}", graph.info().getDetail());
            return;
        }
        String scope = memoryScope(userId, agentCode);
        try {
            graph.ensureScope(scope);
            String userKey = "user_" + sanitizeKey(userId);
            graph.upsertNodes(scope, List.of(
                    new GraphNode(userKey, "User", Map.of("userId", userId)),
                    new GraphNode(graphKey, "Memory", Map.of(
                            "memoryId", String.valueOf(row.getId()),
                            "category", row.getCategory(),
                            "content", row.getContent(),
                            "documentId", graphKey
                    ))
            ));
            graph.upsertEdges(scope, List.of(
                    new GraphEdge(userKey, graphKey, "HAS_MEMORY", Map.of())
            ));
            if (entities != null) {
                List<GraphNode> ents = new ArrayList<>();
                List<GraphEdge> about = new ArrayList<>();
                for (String name : entities) {
                    if (!StringUtils.hasText(name)) {
                        continue;
                    }
                    String ek = "ent_" + sanitizeKey(name);
                    ents.add(new GraphNode(ek, "Entity", Map.of("name", name.trim())));
                    about.add(new GraphEdge(graphKey, ek, "ABOUT", Map.of()));
                }
                if (!ents.isEmpty()) {
                    graph.upsertNodes(scope, ents);
                    graph.upsertEdges(scope, about);
                }
            }
        } catch (Exception e) {
            log.warn("LTM graph sync failed: {}", e.getMessage());
        }
    }

    static String memoryScope(String userId, String agentCode) {
        String u = StringUtils.hasText(userId) ? userId : "anon";
        String a = StringUtils.hasText(agentCode) ? agentCode : "*";
        return "mem_" + sanitizeKey(u) + "_" + sanitizeKey(a);
    }

    static String sanitizeKey(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (s.isEmpty()) {
            return "x";
        }
        if (!Character.isLetter(s.charAt(0)) && s.charAt(0) != '_') {
            s = "k_" + s;
        }
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    /** 记忆行转为对外条目。 */
    private MemoryItem toItem(AiLongTermMemory row, double score) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (StringUtils.hasText(row.getGraphKey())) {
            meta.put("graphKey", row.getGraphKey());
        }
        if (StringUtils.hasText(row.getEntitiesJson())) {
            try {
                meta.put("entities", JSON.parseObject(row.getEntitiesJson(), STRING_LIST));
            } catch (Exception ignored) {
                // ignore
            }
        }
        return new MemoryItem(
                String.valueOf(row.getId()),
                row.getUserId(),
                row.getAgentCode(),
                row.getCategory(),
                row.getContent(),
                score,
                meta,
                row.getCreateTime()
        );
    }
}
