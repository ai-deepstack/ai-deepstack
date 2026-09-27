package org.deepstack.ai.graph.age;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.deepstack.ai.graph.GraphEdge;
import org.deepstack.ai.graph.GraphExpandQuery;
import org.deepstack.ai.graph.GraphNeighborhood;
import org.deepstack.ai.graph.GraphNode;
import org.deepstack.ai.graph.GraphProviderInfo;
import org.deepstack.ai.graph.GraphScopeSummary;
import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 基于 Apache AGE 的 {@link GraphStore} 实现。
 */
@Slf4j
@RequiredArgsConstructor
public class AgeGraphStore implements GraphStore {

    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern SAFE_LABEL = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final DeepstackGraphProperties properties;
    private final SysConfigPort sysConfigPort;
    private final ConcurrentHashMap<String, Boolean> ensuredGraphs = new ConcurrentHashMap<>();

    /** 确保图 scope 存在。 */
    @Override
    public void ensureScope(String scope) {
        requireScope(scope);
        String graph = resolveGraphName(scope);
        ensuredGraphs.computeIfAbsent(graph, g -> {
            withAge(conn -> {
                if (!graphExists(conn, g)) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("SELECT create_graph('" + g + "')");
                        log.info("AGE graph created: {}", g);
                    }
                }
                return Boolean.TRUE;
            });
            return Boolean.TRUE;
        });
    }

    /** 批量写入或更新图节点。 */
    @Override
    public void upsertNodes(String scope, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        ensureScope(scope);
        String graph = resolveGraphName(scope);
        withAge(conn -> {
            for (GraphNode node : nodes) {
                String label = sanitizeLabel(node.getLabel());
                Map<String, Object> props = new LinkedHashMap<>(node.getProperties());
                props.put("key", node.getKey());
                props.put("scope", scope);
                String cypher = "MERGE (n:" + label + " {key: " + cypherString(node.getKey())
                        + ", scope: " + cypherString(scope) + "}) "
                        + "SET n += " + toMapLiteral(props) + " "
                        + "RETURN id(n)";
                runCypher(conn, graph, cypher, "v");
            }
            return null;
        });
    }

    /** 批量写入或更新图边。 */
    @Override
    public void upsertEdges(String scope, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        ensureScope(scope);
        String graph = resolveGraphName(scope);
        withAge(conn -> {
            for (GraphEdge edge : edges) {
                String type = sanitizeLabel(edge.getType());
                Map<String, Object> props = new LinkedHashMap<>(edge.getProperties());
                props.put("scope", scope);
                String cypher = "MATCH (a {key: " + cypherString(edge.getFromKey())
                        + ", scope: " + cypherString(scope) + "}), "
                        + "(b {key: " + cypherString(edge.getToKey())
                        + ", scope: " + cypherString(scope) + "}) "
                        + "MERGE (a)-[r:" + type + "]->(b) "
                        + "SET r += " + toMapLiteral(props) + " "
                        + "RETURN id(r)";
                runCypher(conn, graph, cypher, "v");
            }
            return null;
        });
    }

    /** 按文档删除关联图数据。 */
    @Override
    public void deleteByDocument(String scope, String documentId) {
        requireScope(scope);
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        ensureScope(scope);
        String graph = resolveGraphName(scope);
        String cypher = "MATCH (n {scope: " + cypherString(scope)
                + ", documentId: " + cypherString(documentId) + "}) "
                + "DETACH DELETE n "
                + "RETURN 1";
        withAge(conn -> {
            runCypher(conn, graph, cypher, "v");
            return null;
        });
    }

    /** 删除整个图 scope。 */
    @Override
    public void deleteScope(String scope) {
        requireScope(scope);
        String graph = resolveGraphName(scope);
        if (isPropertyIsolation()) {
            ensureScope(scope);
            String cypher = "MATCH (n {scope: " + cypherString(scope) + "}) "
                    + "DETACH DELETE n RETURN 1";
            withAge(conn -> {
                runCypher(conn, graph, cypher, "v");
                return null;
            });
            return;
        }
        withAge(conn -> {
            if (graphExists(conn, graph)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("SELECT drop_graph('" + graph + "', true)");
                    log.info("AGE graph dropped: {}", graph);
                }
                ensuredGraphs.remove(graph);
            }
            return null;
        });
    }

    /** 汇总 scope 的节点与边数量。 */
    @Override
    public GraphScopeSummary summarize(String scope) {
        requireScope(scope);
        ensureScope(scope);
        String graph = resolveGraphName(scope);
        String scopeLit = cypherString(scope);
        log.info("AGE summarize: scope={}, graph={}", scope, graph);
        return withAge(conn -> {
            long nodes = countCypher(conn, graph,
                    "MATCH (n {scope: " + scopeLit + "}) RETURN count(n)");
            long edges = countCypher(conn, graph,
                    "MATCH ()-[r {scope: " + scopeLit + "}]->() RETURN count(r)");
            long docs = countCypher(conn, graph,
                    "MATCH (n:Document {scope: " + scopeLit + "}) RETURN count(n)");
            long chunks = countCypher(conn, graph,
                    "MATCH (n:Chunk {scope: " + scopeLit + "}) RETURN count(n)");
            long entities = countCypher(conn, graph,
                    "MATCH (n:Entity {scope: " + scopeLit + "}) RETURN count(n)");
            log.info("AGE summarize 完成: scope={}, nodes={}, edges={}, docs={}, chunks={}, entities={}",
                    scope, nodes, edges, docs, chunks, entities);
            return new GraphScopeSummary(nodes, edges, docs, chunks, entities);
        });
    }

    /** 删除无引用的实体节点。 */
    @Override
    public int deleteOrphanEntities(String scope) {
        requireScope(scope);
        ensureScope(scope);
        String graph = resolveGraphName(scope);
        String scopeLit = cypherString(scope);
        int deleted = 0;
        // 不再挂到 Chunk(MENTIONS) / Document(ABOUT) 上的实体，含「只剩 RELATED_TO」的簇
        String predicate = "WHERE NOT (n)-[:MENTIONS]-() AND NOT (n)-[:ABOUT]-()";
        boolean fellBack = false;
        for (int round = 0; round < 20; round++) {
            final String findQuery = "MATCH (n:Entity {scope: " + scopeLit + "}) "
                    + predicate + " RETURN n.key LIMIT 200";
            List<String> keys;
            try {
                keys = withAge(conn -> readScalarStrings(conn, graph, findQuery));
            } catch (Exception e) {
                if (fellBack) {
                    log.warn("AGE deleteOrphanEntities 回退后仍失败: scope={}, err={}",
                            scope, e.getMessage());
                    break;
                }
                log.warn("AGE 按 MENTIONS/ABOUT 找孤儿失败，回退无边条件: scope={}, err={}",
                        scope, e.getMessage());
                fellBack = true;
                predicate = "WHERE NOT (n)--()";
                final String fallbackQuery = "MATCH (n:Entity {scope: " + scopeLit + "}) "
                        + predicate + " RETURN n.key LIMIT 200";
                keys = withAge(conn -> readScalarStrings(conn, graph, fallbackQuery));
            }
            if (keys.isEmpty()) {
                break;
            }
            log.info("AGE deleteOrphanEntities: scope={}, round={}, batch={}, predicate={}",
                    scope, round, keys.size(), predicate);
            final String deletePredicate = predicate;
            for (String key : keys) {
                final String cypher = "MATCH (n:Entity {key: " + cypherString(key)
                        + ", scope: " + scopeLit + "}) "
                        + deletePredicate + " DETACH DELETE n RETURN 1";
                withAge(conn -> {
                    runCypher(conn, graph, cypher, "v");
                    return null;
                });
                deleted++;
            }
        }
        log.info("AGE deleteOrphanEntities 完成: scope={}, deleted={}", scope, deleted);
        return deleted;
    }

    /** 从起点按跳数扩展邻居节点。 */
    @Override
    public List<GraphNode> expand(GraphExpandQuery query) {
        requireScope(query.getScope());
        ensureScope(query.getScope());
        String graph = resolveGraphName(query.getScope());
        int hops = Math.min(query.getHops(), 5);
        int limit = Math.min(query.getLimit(), 200);
        String relFilter = buildRelFilter(query.getEdgeTypes());
        // 带 min(length(r)) → 属性 hop，供召回 graphScore = 0.7/(1+hop)
        String cypherWithHop = "MATCH (s {key: " + cypherString(query.getStartKey())
                + ", scope: " + cypherString(query.getScope()) + "})"
                + "-[r" + relFilter + "*1.." + hops + "]-(n) "
                + "WHERE n.scope = " + cypherString(query.getScope()) + " "
                + "RETURN n, min(length(r)) AS hop "
                + "ORDER BY hop LIMIT " + limit;
        log.debug("AGE expand: scope={}, startKey={}, hops={}, limit={}, relFilter={}",
                query.getScope(), query.getStartKey(), hops, limit, relFilter);
        try {
            List<GraphNode> nodes = withAge(conn -> readNodesWithHop(conn, graph, cypherWithHop));
            log.info("AGE expand 完成(含hop): scope={}, startKey={}, neighbors={}",
                    query.getScope(), query.getStartKey(), nodes.size());
            return nodes;
        } catch (Exception e) {
            // 部分 AGE 版本对 min(length(r)) / 双列 RETURN 支持不佳 → 回退无 hop
            log.warn("AGE expand 含 hop 失败，回退无 hop: scope={}, err={}",
                    query.getScope(), e.getMessage());
            String cypher = "MATCH (s {key: " + cypherString(query.getStartKey())
                    + ", scope: " + cypherString(query.getScope()) + "})"
                    + "-[r" + relFilter + "*1.." + hops + "]-(n) "
                    + "WHERE n.scope = " + cypherString(query.getScope()) + " "
                    + "RETURN DISTINCT n LIMIT " + limit;
            List<GraphNode> nodes = withAge(conn -> readNodes(conn, graph, cypher));
            log.info("AGE expand 完成(无hop回退): scope={}, startKey={}, neighbors={}",
                    query.getScope(), query.getStartKey(), nodes.size());
            return nodes;
        }
    }

    /**
     * 多跳扩展并带回路径上出现的边。
     * <p>
     * 先 {@link #expand} 取邻居节点；再尝试从起点按变长路径收集边。
     * 若 AGE 对 {@code relationships(path)} 支持不佳，则回退为：
     * 在「起点 + 邻居」key 集合上做 1-hop 有向边查询。
     * </p>
     */
    @Override
    public GraphNeighborhood expandNeighborhood(GraphExpandQuery query) {
        log.info("AGE expandNeighborhood: scope={}, startKey={}, hops={}, limit={}, edgeTypes={}",
                query.getScope(), query.getStartKey(), query.getHops(), query.getLimit(),
                query.getEdgeTypes());
        List<GraphNode> nodes = expand(query);
        Set<String> keys = new LinkedHashSet<>();
        keys.add(query.getStartKey());
        for (GraphNode n : nodes) {
            if (n != null && StringUtils.hasText(n.getKey())) {
                keys.add(n.getKey());
            }
        }
        if (keys.size() <= 1) {
            log.debug("AGE expandNeighborhood: 仅起点无邻居, scope={}, startKey={}",
                    query.getScope(), query.getStartKey());
            return new GraphNeighborhood(nodes, List.of());
        }

        List<GraphEdge> edges;
        try {
            edges = collectPathEdges(query, keys);
            if (edges.isEmpty()) {
                log.debug("AGE expandNeighborhood: 路径边为空，回退 pairwise 边查询");
                edges = collectPairwiseEdges(query.getScope(), keys, query.getEdgeTypes());
            }
        } catch (Exception e) {
            log.warn("AGE expandNeighborhood 路径边收集失败，回退 pairwise: scope={}, err={}",
                    query.getScope(), e.getMessage());
            edges = collectPairwiseEdges(query.getScope(), keys, query.getEdgeTypes());
        }
        log.info("AGE expandNeighborhood 完成: scope={}, startKey={}, nodes={}, edges={}",
                query.getScope(), query.getStartKey(), nodes.size(), edges.size());
        return new GraphNeighborhood(nodes, edges);
    }

    /**
     * 列出带 documentId 属性的节点指向同 scope 节点的直接边（控制台按文档过滤）。
     */
    @Override
    public List<GraphEdge> listDocumentEdges(String scope, String documentId) {
        requireScope(scope);
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        ensureScope(scope);
        String graph = resolveGraphName(scope);
        // documentId 在 AGE 属性中可能是字符串；与写入侧 String.valueOf 对齐
        String cypher = "MATCH (a {scope: " + cypherString(scope)
                + ", documentId: " + cypherString(documentId) + "})"
                + "-[r]->(b {scope: " + cypherString(scope) + "}) "
                + "RETURN a.key, type(r), b.key";
        log.info("AGE listDocumentEdges: scope={}, documentId={}", scope, documentId);
        List<GraphEdge> edges = withAge(conn -> readTriples(conn, graph, cypher));
        log.info("AGE listDocumentEdges 完成: scope={}, documentId={}, edges={}",
                scope, documentId, edges.size());
        return edges;
    }

    /** 返回图存储提供方状态。 */
    @Override
    public GraphProviderInfo info() {
        if (!sysConfigPort.isYes(SysConfigKeys.GRAPH_ENABLED)) {
            return new GraphProviderInfo("age", false, "graph.enabled=0");
        }
        try {
            Boolean ok = withAge(conn -> {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT 1 FROM pg_extension WHERE extname = 'age'")) {
                    return rs.next();
                }
            });
            if (Boolean.TRUE.equals(ok)) {
                return new GraphProviderInfo("age", true,
                        "isolation=" + properties.getAge().getIsolation()
                                + ", defaultGraph=" + properties.getAge().getDefaultGraph());
            }
            return new GraphProviderInfo("age", false, "PostgreSQL 未安装 age 扩展");
        } catch (Exception e) {
            return new GraphProviderInfo("age", false, e.getMessage());
        }
    }

    /**
     * 尝试用变长路径 + relationships 收集边；AGE 不支持时抛异常由调用方回退。
     */
    private List<GraphEdge> collectPathEdges(GraphExpandQuery query, Set<String> keys) {
        String graph = resolveGraphName(query.getScope());
        int hops = Math.min(query.getHops(), 5);
        String relFilter = buildRelFilter(query.getEdgeTypes());
        // AGE 对 relationships()/startNode()/endNode() 支持因版本而异；失败则上层回退
        String cypher = "MATCH path = (s {key: " + cypherString(query.getStartKey())
                + ", scope: " + cypherString(query.getScope()) + "})"
                + "-[r" + relFilter + "*1.." + hops + "]-(n) "
                + "WHERE n.scope = " + cypherString(query.getScope()) + " "
                + "UNWIND relationships(path) AS rel "
                + "RETURN DISTINCT startNode(rel).key, type(rel), endNode(rel).key "
                + "LIMIT " + Math.min(query.getLimit() * 4, 400);
        log.debug("AGE collectPathEdges cypher hops={}, keyCount={}", hops, keys.size());
        return withAge(conn -> readTriples(conn, graph, cypher));
    }

    /**
     * 回退：在给定 key 集合上查 1-hop 有向边（两端都在集合内且同 scope）。
     */
    private List<GraphEdge> collectPairwiseEdges(String scope, Set<String> keys, List<String> edgeTypes) {
        if (keys == null || keys.size() < 2) {
            return List.of();
        }
        ensureScope(scope);
        String graph = resolveGraphName(scope);
        String inList = toCypherStringList(keys);
        String typeFilter = "";
        if (edgeTypes != null && !edgeTypes.isEmpty()) {
            List<String> types = new ArrayList<>();
            for (String t : edgeTypes) {
                types.add(sanitizeLabel(t));
            }
            typeFilter = " AND type(r) IN [" + String.join(", ",
                    types.stream().map(AgeGraphStore::cypherString).toList()) + "]";
        }
        String cypher = "MATCH (a)-[r]->(b) "
                + "WHERE a.scope = " + cypherString(scope)
                + " AND b.scope = " + cypherString(scope)
                + " AND a.key IN " + inList
                + " AND b.key IN " + inList
                + typeFilter + " "
                + "RETURN a.key, type(r), b.key";
        log.debug("AGE collectPairwiseEdges: scope={}, keys={}, edgeTypes={}",
                scope, keys.size(), edgeTypes);
        return withAge(conn -> readTriples(conn, graph, cypher));
    }

    /** 拼 Cypher 关系类型过滤。 */
    private static String buildRelFilter(List<String> edgeTypes) {
        if (edgeTypes == null || edgeTypes.isEmpty()) {
            return "";
        }
        List<String> types = new ArrayList<>();
        for (String t : edgeTypes) {
            types.add(sanitizeLabel(t));
        }
        return ":" + String.join("|", types);
    }

    /** 把键集合转为 Cypher 字符串列表。 */
    private static String toCypherStringList(Set<String> keys) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String k : keys) {
            if (!StringUtils.hasText(k)) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(cypherString(k));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 执行返回 (fromKey, type, toKey) 三列的 cypher。
     */
    private List<GraphEdge> readTriples(Connection conn, String graph, String cypher) throws SQLException {
        List<GraphEdge> out = new ArrayList<>();
        String sql = "SELECT * FROM cypher(?, $$" + cypher + "$$) AS (fk agtype, rt agtype, tk agtype)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, graph);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String from = stripAgtype(rs.getString(1));
                    String type = stripAgtype(rs.getString(2));
                    String to = stripAgtype(rs.getString(3));
                    if (StringUtils.hasText(from) && StringUtils.hasText(type) && StringUtils.hasText(to)) {
                        try {
                            out.add(new GraphEdge(from, to, sanitizeLabel(type), Map.of()));
                        } catch (IllegalArgumentException ex) {
                            log.warn("AGE 跳过非法边类型: type={}, from={}, to={}", type, from, to);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** 去掉 AGE agtype 后缀（如 {@code "chk_1":: agtype} / 引号）。 */
    private static String stripAgtype(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        int cut = s.indexOf("::");
        if (cut > 0) {
            s = s.substring(0, cut).trim();
        }
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 执行 Cypher 并读取节点。 */
    private List<GraphNode> readNodes(Connection conn, String graph, String cypher) throws SQLException {
        List<GraphNode> out = new ArrayList<>();
        String sql = "SELECT * FROM cypher(?, $$" + cypher + "$$) AS (n agtype)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, graph);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GraphNode node = parseVertex(rs.getString(1));
                    if (node != null) {
                        out.add(node);
                    }
                }
            }
        }
        return out;
    }

    /** 单列计数 cypher（RETURN count(...)）。 */
    private long countCypher(Connection conn, String graph, String cypher) throws SQLException {
        String sql = "SELECT * FROM cypher(?, $$" + cypher + "$$) AS (c agtype)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, graph);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                String raw = stripAgtype(rs.getString(1));
                if (!StringUtils.hasText(raw)) {
                    return 0L;
                }
                try {
                    return Long.parseLong(raw.trim());
                } catch (NumberFormatException e) {
                    log.warn("AGE count 无法解析: raw={}", raw);
                    return 0L;
                }
            }
        }
    }

    /** 单列字符串列表（如 n.key）。 */
    private List<String> readScalarStrings(Connection conn, String graph, String cypher) throws SQLException {
        List<String> out = new ArrayList<>();
        String sql = "SELECT * FROM cypher(?, $$" + cypher + "$$) AS (c agtype)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, graph);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = stripAgtype(rs.getString(1));
                    if (StringUtils.hasText(v)) {
                        out.add(v);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 读取带 hop 列的 expand 结果，写入节点属性 {@code hop}（Integer）。
     */
    private List<GraphNode> readNodesWithHop(Connection conn, String graph, String cypher) throws SQLException {
        List<GraphNode> out = new ArrayList<>();
        String sql = "SELECT * FROM cypher(?, $$" + cypher + "$$) AS (n agtype, hop agtype)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, graph);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GraphNode node = parseVertex(rs.getString(1));
                    if (node == null) {
                        continue;
                    }
                    Integer hop = parseHopAgtype(rs.getString(2));
                    if (hop == null) {
                        out.add(node);
                        continue;
                    }
                    Map<String, Object> props = new LinkedHashMap<>(node.getProperties());
                    props.put("hop", hop);
                    out.add(new GraphNode(node.getKey(), node.getLabel(), props));
                }
            }
        }
        return out;
    }

    /** 解析 AGE agtype 整型 hop（如 {@code 1} / {@code 1::agtype}）。 */
    private static Integer parseHopAgtype(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        int cut = s.indexOf("::");
        if (cut > 0) {
            s = s.substring(0, cut).trim();
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 执行一条 Cypher。 */
    private void runCypher(Connection conn, String graph, String cypher, String alias) throws SQLException {
        String sql = "SELECT * FROM cypher(?, $$" + cypher + "$$) AS (" + alias + " agtype)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, graph);
            ps.execute();
        }
    }

    /** 判断图是否已存在。 */
    private boolean graphExists(Connection conn, String graph) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM ag_catalog.ag_graph WHERE name = ?")) {
            ps.setString(1, graph);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** 在 AGE 会话中执行数据库回调。 */
    private <T> T withAge(ConnectionCallback<T> action) {
        return jdbcTemplate.execute((ConnectionCallback<T>) conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute("LOAD 'age'");
                st.execute("SET search_path = ag_catalog, \"$user\", public, deepstack");
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "无法加载 Apache AGE（请确认已安装扩展并执行 sql/04_apache_age.sql）: "
                                + e.getMessage(), e);
            }
            return action.doInConnection(conn);
        });
    }

    /** 由 scope 解析 AGE 图名。 */
    private String resolveGraphName(String scope) {
        if (isPropertyIsolation()) {
            String name = properties.getAge().getDefaultGraph();
            if (!SAFE_IDENT.matcher(name).matches()) {
                throw new IllegalArgumentException("非法 defaultGraph: " + name);
            }
            return name;
        }
        String raw = "ds_" + scope.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (raw.length() > 63) {
            raw = raw.substring(0, 63);
        }
        if (!SAFE_IDENT.matcher(raw).matches()) {
            throw new IllegalArgumentException("非法 scope 图名: " + raw);
        }
        return raw;
    }

    /** 是否 PropertyIsolation。 */
    private boolean isPropertyIsolation() {
        return !"GRAPH".equalsIgnoreCase(properties.getAge().getIsolation());
    }

    /** 校验 scope 非空。 */
    private static void requireScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            throw new IllegalArgumentException("scope 不能为空");
        }
    }

    /** 清洗图标签，避免注入。 */
    private static String sanitizeLabel(String label) {
        if (!StringUtils.hasText(label) || !SAFE_LABEL.matcher(label).matches()) {
            throw new IllegalArgumentException("非法标签/关系类型: " + label);
        }
        return label;
    }

    static String cypherString(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    static String toMapLiteral(Map<String, Object> props) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : props.entrySet()) {
            if (!SAFE_IDENT.matcher(e.getKey()).matches()) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(e.getKey()).append(": ").append(toLiteral(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    static String toLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return cypherString(String.valueOf(value));
    }

    GraphNode parseVertex(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            String json = raw;
            int cut = json.indexOf("::");
            if (cut > 0) {
                json = json.substring(0, cut);
            }
            JSONObject root = JSON.parseObject(json);
            String label = textOr(root.getString("label"), "Node");
            Map<String, Object> props = new LinkedHashMap<>();
            JSONObject propsNode = root.getJSONObject("properties");
            if (propsNode != null) {
                props.putAll(propsNode);
            }
            String key = props.get("key") == null ? null : String.valueOf(props.get("key"));
            if (!StringUtils.hasText(key)) {
                key = textOr(root.getString("id"), null);
            }
            if (!StringUtils.hasText(key)) {
                return null;
            }
            return new GraphNode(key, label, props);
        } catch (Exception e) {
            log.warn("解析 AGE vertex 失败: {}", raw, e);
            return null;
        }
    }

    /** 读取 JSON 文本，缺失时用默认值。 */
    private static String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
