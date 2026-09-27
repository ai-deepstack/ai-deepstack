package org.deepstack.ai.engine.validate;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 工作流图结构的编译期校验器。
 * <p>
 * 在编译前校验工作流定义的结构完整性：
 * <ul>
 *   <li>恰好存在一个 start-node，且至少一个 end-node</li>
 *   <li>无孤立节点（除 start 外每个节点须有入边）</li>
 *   <li>无悬空边（source/target 节点 ID 必须存在于节点集合）</li>
 *   <li>每个非 end 节点至少有一条出边</li>
 *   <li>SEQ/DAG 类型检测环（GRAPH 类型允许有环）</li>
 *   <li>可达性：所有节点须可从 start-node 到达</li>
 * </ul>
 * </p>
 * <p>
 * 在编译期捕获结构错误（快速失败），避免运行时静默失败
 * （例如某节点无出边导致工作流挂起）。
 * </p>
 *
 */
@Slf4j
public final class WorkflowDefinitionValidator {

    private WorkflowDefinitionValidator() {}

    /**
     * 校验工作流图结构。
     *
     * @param workflowCode 工作流编码（用于错误信息）
     * @param nodes        节点定义 map 列表（每项须含 "id"、"type"）
     * @param edges        边定义 map 列表（每项须含 "sourceNodeId"、"targetNodeId"）
     * @throws IllegalArgumentException 发现任意结构错误时抛出
     */
    public static void validate(String workflowCode, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        validate(workflowCode, nodes, edges, null);
    }

    /**
     * 校验工作流定义。
     *
     * @param workflowCode 工作流编码（用于错误信息）
     * @param nodes        节点列表
     * @param edges        边列表
     * @param workflowType 工作流类型（SEQ、DAG、GRAPH）；仅 GRAPH 允许有环
     * @throws IllegalArgumentException 发现任意结构错误时抛出
     */
    @SuppressWarnings("unchecked")
    public static void validate(String workflowCode, List<Map<String, Object>> nodes,
                                List<Map<String, Object>> edges, String workflowType) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow '" + workflowCode + "' has no nodes");
        }

        // 1. 提取节点 ID 与类型
        Set<String> nodeIds = new LinkedHashSet<>();
        Map<String, String> nodeTypes = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Workflow '" + workflowCode + "' contains a node without an id");
            }
            if (!nodeIds.add(id)) {
                throw new IllegalArgumentException("Workflow '" + workflowCode + "' has duplicate node id: " + id);
            }
            String type = (String) node.get("type");
            nodeTypes.put(id, type != null ? type : "unknown");
        }

        // 2. 检查 start-node（恰好一个）
        List<String> startNodes = nodeTypes.entrySet().stream()
                .filter(e -> "start-node".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (startNodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow '" + workflowCode + "' has no start-node");
        }
        if (startNodes.size() > 1) {
            throw new IllegalArgumentException("Workflow '" + workflowCode + "' has multiple start-nodes: " + startNodes);
        }
        String startNodeId = startNodes.get(0);

        // 3. 检查 end-node（至少一个）
        List<String> endNodes = nodeTypes.entrySet().stream()
                .filter(e -> "end-node".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (endNodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow '" + workflowCode + "' has no end-node");
        }

        // 4. 校验边：禁止悬空引用
        Set<String> nodesWithOutgoing = new HashSet<>();
        Set<String> nodesWithIncoming = new HashSet<>();
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (String id : nodeIds) {
            adjacency.put(id, new HashSet<>());
        }

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("sourceNodeId");
                String target = (String) edge.get("targetNodeId");

                if (source == null || source.isBlank()) {
                    throw new IllegalArgumentException("Workflow '" + workflowCode + "' has an edge without sourceNodeId");
                }
                if (target == null || target.isBlank()) {
                    throw new IllegalArgumentException("Workflow '" + workflowCode + "' has an edge without targetNodeId");
                }
                if (!nodeIds.contains(source)) {
                    throw new IllegalArgumentException("Workflow '" + workflowCode
                            + "' has an edge with unknown source node: " + source);
                }
                if (!nodeIds.contains(target)) {
                    throw new IllegalArgumentException("Workflow '" + workflowCode
                            + "' has an edge with unknown target node: " + target);
                }

                nodesWithOutgoing.add(source);
                nodesWithIncoming.add(target);
                adjacency.get(source).add(target);
            }
        }

        // 5. 检查孤立节点（无入边，排除 start-node）
        for (String id : nodeIds) {
            if (!nodesWithIncoming.contains(id) && !startNodeId.equals(id)) {
                throw new IllegalArgumentException("Workflow '" + workflowCode
                        + "' has orphan node '" + id + "' (no incoming edge, not a start-node)");
            }
        }

        // 6. 每个非 end 节点至少有一条出边
        for (String id : nodeIds) {
            if (!"end-node".equals(nodeTypes.get(id)) && !nodesWithOutgoing.contains(id)) {
                throw new IllegalArgumentException("Workflow '" + workflowCode
                        + "' node '" + id + "' has no outgoing edge (must be an end-node or have outgoing edges)");
            }
        }

        // 7. 可达性：所有节点须可从 start-node 到达
        Set<String> reachable = bfs(startNodeId, adjacency);
        for (String id : nodeIds) {
            if (!reachable.contains(id)) {
                throw new IllegalArgumentException("Workflow '" + workflowCode
                        + "' node '" + id + "' is unreachable from start-node '" + startNodeId + "'");
            }
        }

        // 8. 环检测（SEQ/DAG；GRAPH 允许有环）
        List<String> cycle = detectCycle(nodeIds, adjacency);
        if (cycle != null && !"GRAPH".equals(workflowType)) {
            throw new IllegalArgumentException("Workflow '" + workflowCode
                    + "' contains a cycle: " + String.join(" -> ", cycle)
                    + " -> " + cycle.get(0) + ". Cycles are only allowed in GRAPH type workflows.");
        }
        if (cycle != null && "GRAPH".equals(workflowType)) {
            log.warn("Workflow '{}' contains a cycle: {} -> {}. "
                    + "Allowed for GRAPH type — ensure a runtime recursion limit is set.",
                    workflowCode, String.join(" -> ", cycle), cycle.get(0));
        }
    }

    /**
     * 从起始节点做广度优先遍历，收集全部可达节点。
     */
    private static Set<String> bfs(String start, Map<String, Set<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> neighbors = adjacency.get(current);
            if (neighbors != null) {
                for (String next : neighbors) {
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return visited;
    }

    /**
     * 使用 DFS 检测图中是否存在环。
     * 发现则返回环路径，无环则返回 null。
     */
    private static List<String> detectCycle(Set<String> nodeIds, Map<String, Set<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        Map<String, String> parent = new HashMap<>();

        for (String node : nodeIds) {
            if (!visited.contains(node)) {
                List<String> cycle = dfsCycle(node, adjacency, visited, inStack, parent);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        return null;
    }

    /**
     * 基于 DFS 的环检测。发现则返回环路径。
     */
    private static List<String> dfsCycle(String start, Map<String, Set<String>> adjacency,
                                         Set<String> visited, Set<String> inStack,
                                         Map<String, String> parent) {
        Stack<String> stack = new Stack<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            String current = stack.peek();
            if (!visited.contains(current)) {
                visited.add(current);
                inStack.add(current);
            }

            Set<String> neighbors = adjacency.get(current);
            boolean hasUnvisited = false;
            if (neighbors != null) {
                for (String next : neighbors) {
                    if (inStack.contains(next)) {
                        // 发现环 —— 回溯重建路径
                        List<String> cycle = new ArrayList<>();
                        String node = current;
                        while (node != null && !node.equals(next)) {
                            cycle.add(0, node);
                            node = parent.get(node);
                        }
                        if (node != null) {
                            cycle.add(0, next);
                        }
                        return cycle;
                    }
                    if (!visited.contains(next)) {
                        parent.put(next, current);
                        stack.push(next);
                        hasUnvisited = true;
                        break;
                    }
                }
            }

            if (!hasUnvisited) {
                inStack.remove(current);
                stack.pop();
            }
        }
        return null;
    }
}
