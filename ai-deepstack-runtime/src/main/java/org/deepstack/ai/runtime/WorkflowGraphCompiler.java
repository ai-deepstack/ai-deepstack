package org.deepstack.ai.runtime;

import org.deepstack.ai.engine.api.GraphSpec;
import org.deepstack.ai.engine.api.TraceConfig;
import org.deepstack.ai.engine.api.WorkflowState;
import org.deepstack.ai.engine.api.WorkflowStreamEvent;
import org.deepstack.ai.engine.support.ConditionEvaluator;
import org.deepstack.ai.engine.support.NodeTimeoutConfig;
import org.deepstack.ai.engine.validate.NodePropertiesValidator;
import org.deepstack.ai.engine.validate.WorkflowDefinitionValidator;
import org.deepstack.ai.kernel.enums.agent.TraceModeEnum;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 工作流图编译器：把 JSON 图定义编译为 LangGraph4j {@link CompiledGraph}，并独占编译缓存。
 * <p>
 * 只负责「定义 → 图」的构建与缓存（{@link #invalidateCache} 同源），
 * 节点动作的构造/包装交由 {@link NodeActionFactory}；执行、流式与 HITL resume 不在本类职责内。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowGraphCompiler {

    private final NodeActionFactory nodeActionFactory;
    private final ObjectProvider<BaseCheckpointSaver> checkpointSaver;
    private final SysConfigPort sysConfigPort;

    /**
     * 编译缓存：cacheKey（workflowId + version + definition 哈希 + traceMode）→ CompiledGraph。
     * 避免对同一工作流每次请求都重新解析 JSON 并重建图。
     */
    private final ConcurrentHashMap<String, CompiledGraph<WorkflowState>> compileCache = new ConcurrentHashMap<>();

    /**
     * 编译缓存最大条目数。超出时移除较旧的条目。
     */
    private static final int COMPILE_CACHE_MAX_SIZE = 200;

    /**
     * 将工作流 JSON 定义编译为 LangGraph4j CompiledGraph。
     * 使用默认 TraceConfig（RECORD 模式，不推流）。
     * 结果按 workflowId + version + definition 哈希 + traceMode 缓存。
     */
    public CompiledGraph<WorkflowState> compile(GraphSpec workflow) {
        log.info("Workflow compile (default TraceConfig): workflowId={}, code={}, version={}",
                workflow.getId(), workflow.getCode(), workflow.getVersion());
        return compile(workflow, new TraceConfig());
    }

    /**
     * 按显式 TraceConfig 编译工作流 JSON 定义。
     * TraceConfig 控制节点是否记录执行轨迹以及/或推送流式事件。
     * 结果按 workflowId + version + definition 哈希 + traceMode 缓存。
     */
    @SuppressWarnings("unchecked")
    public CompiledGraph<WorkflowState> compile(GraphSpec workflow, TraceConfig traceConfig) {
        log.info("Workflow compile: workflowId={}, code={}, version={}, traceMode={}",
                workflow.getId(), workflow.getCode(), workflow.getVersion(),
                traceConfig != null ? traceConfig.getTraceMode() : null);
        String cacheKey = buildCacheKey(workflow, traceConfig);
        CompiledGraph<WorkflowState> cached = compileCache.get(cacheKey);
        if (cached != null) {
            log.debug("Workflow compile cache hit: key={}", cacheKey);
            return cached;
        }

        log.debug("Workflow compile cache miss: key={}", cacheKey);
        CompiledGraph<WorkflowState> compiled = doCompile(workflow, traceConfig);
        log.info("Workflow compile done: workflowId={}, code={}, cacheKey={}",
                workflow.getId(), workflow.getCode(), cacheKey);
        // 写入编译缓存（容量守卫：满容时随机移除一条）
        if (compileCache.size() >= COMPILE_CACHE_MAX_SIZE) {
            // 只删一条而非清空全部（避免编译风暴）
            var iter = compileCache.entrySet().iterator();
            if (iter.hasNext()) {
                iter.next();
                iter.remove();
            }
            log.debug("Workflow compile cache evicted one entry (at {} capacity)", COMPILE_CACHE_MAX_SIZE);
        }
        compileCache.putIfAbsent(cacheKey, compiled);
        return compiled;
    }

    /**
     * 使用感知流式的节点包装器编译工作流，并向 sink 推送事件。
     */
    public CompiledGraph<WorkflowState> compileWithStream(GraphSpec workflow, TraceConfig traceConfig,
                                                          Sinks.Many<WorkflowStreamEvent> sink) {
        return doCompile(workflow, traceConfig, sink);
    }

    /**
     * 使工作流相关缓存失效（定义更新时应调用）。
     */
    public void invalidateCache(Long workflowId) {
        if (workflowId == null) return;
        String prefix = "wf:" + workflowId + ":";
        int removed = 0;
        var it = compileCache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey().startsWith(prefix)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Workflow compile cache invalidated: workflowId={}, entries={}", workflowId, removed);
        }
    }

    /**
     * 当前编译缓存条目数（供健康监控）。
     */
    public int getCacheSize() {
        return compileCache.size();
    }

    /**
     * 构建缓存键：wf:{workflowId}:{version}:{definitionHash}:{defaultModel}:{traceMode}
     */
    private String buildCacheKey(GraphSpec workflow, TraceConfig traceConfig) {
        String defHash = sha256Short(workflow.getDefinition());
        String traceMode = traceConfig != null ? traceConfig.getTraceMode() : TraceModeEnum.RECORD.name();
        String defaultModel = workflow.getDefaultModelCode() != null ? workflow.getDefaultModelCode() : "";
        return "wf:" + workflow.getId() + ":" + workflow.getVersion() + ":" + defHash
                + ":" + defaultModel + ":" + traceMode;
    }

    /**
     * 计算 definition 字符串的 SHA-256 哈希（前 16 位十六进制）。
     */
    private String sha256Short(String input) {
        if (input == null || input.isEmpty()) return "empty";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // 回退：使用 hashCode
        return "h" + Integer.toHexString(input.hashCode());
        }
    }

    /**
     * 实际编译逻辑（不做缓存）。无 sink，不推送流式节点事件。
     */
    private CompiledGraph<WorkflowState> doCompile(GraphSpec workflow, TraceConfig traceConfig) {
        return doCompile(workflow, traceConfig, null);
    }

    /**
     * 实际编译逻辑（不做缓存）。
     * {@code sink} 非空或 {@link TraceConfig#shouldStream()} 时，节点包装器会推送 NODE_START / NODE_COMPLETE。
     * <p>
     * 工作流级默认超时为方法内局部变量（取自 executionConfig.defaultTimeoutMs），
     * 而非实例字段，以避免本单例上并发 compile 时产生竞态。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private CompiledGraph<WorkflowState> doCompile(GraphSpec workflow, TraceConfig traceConfig,
                                                   Sinks.Many<WorkflowStreamEvent> sink) {
        Map<String, Object> definition = parseDefinition(workflow.getDefinition());
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition is null: " + workflow.getCode());
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.getOrDefault("nodes", List.of());
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.getOrDefault("edges", List.of());
        Map<String, Object> variables = (Map<String, Object>) definition.getOrDefault("variables", Map.of());

        // 校验图结构（孤立节点、环、缺少起止节点、悬空边）
        WorkflowDefinitionValidator.validate(workflow.getCode(), nodes, edges, workflow.getGraphType());

        // 从 executionConfig 提取工作流级默认超时（局部变量，非实例字段）
        Map<String, Object> executionConfig = (Map<String, Object>) definition.getOrDefault("executionConfig", Map.of());
        Integer workflowDefaultTimeoutMs = extractWorkflowDefaultTimeout(executionConfig);

        // 构建 State schema
        Map<String, Channel<?>> schema = WorkflowState.defaultSchema();
        variables.keySet().forEach(k -> schema.put(k, Channels.base(() -> "")));

        try {
            StateGraph<WorkflowState> graph = new StateGraph<>(schema, WorkflowState::new);

            // 1. 注册全部节点（按 TraceConfig 包装执行跟踪）
            for (Map<String, Object> nodeDef : nodes) {
                String nodeId = (String) nodeDef.get("id");
                String type = (String) nodeDef.get("type");
                String name = (String) nodeDef.getOrDefault("name", nodeId);
                Map<String, Object> properties = mutableProperties(
                        (Map<String, Object>) nodeDef.getOrDefault("properties", Map.of()));
                applyDefaultModelCode(type, properties, workflow.getDefaultModelCode());

                // 编译期校验必填属性（快速失败）
                NodePropertiesValidator.validate(nodeId, type, properties);

                AsyncNodeAction<WorkflowState> action = nodeActionFactory.createNodeAction(type, properties, nodeId, name);

                // 解析节点级超时
                NodeTimeoutConfig timeoutConfig = NodeTimeoutConfig.resolve(properties, workflowDefaultTimeoutMs);
                AsyncNodeAction<WorkflowState> timedAction = nodeActionFactory.wrapWithTimeout(nodeId, action, timeoutConfig);

                boolean autoHitl = checkpointSaver.getIfAvailable() != null
                        && sysConfigPort.isYes(SysConfigKeys.CHECKPOINT_ENABLED);
                AsyncNodeAction<WorkflowState> tracked = nodeActionFactory.wrapWithTracking(
                        nodeId, name, type, timedAction, action, autoHitl, traceConfig, sink);
                graph.addNode(nodeId, tracked);
            }

            // 2. 收集条件边
            Map<String, List<Map<String, Object>>> conditionalEdges = new LinkedHashMap<>();
            List<Map<String, Object>> normalEdges = new ArrayList<>();

            for (Map<String, Object> edgeDef : edges) {
                String source = (String) edgeDef.get("sourceNodeId");
                Map<String, Object> props = (Map<String, Object>) edgeDef.getOrDefault("properties", Map.of());
                String condition = props != null ? (String) props.get("condition") : null;

                if (condition != null && !condition.isEmpty()) {
                    conditionalEdges.computeIfAbsent(source, k -> new ArrayList<>()).add(edgeDef);
                } else {
                    normalEdges.add(edgeDef);
                }
            }

            // 3. 注册普通边
            for (Map<String, Object> edgeDef : normalEdges) {
                String source = (String) edgeDef.get("sourceNodeId");
                String target = (String) edgeDef.get("targetNodeId");
                // 若该 source 同时有条件边，则跳过普通边。
                // LangGraph4j 不允许同一 source 节点同时存在普通边与条件边
                if (conditionalEdges.containsKey(source)) {
                    log.debug("Skipping normal edge {} -> {} because source has conditional edges", source, target);
                    continue;
                }
                graph.addEdge(source, target);
            }

            // 4. 注册条件边（增强：支持任意状态变量表达式）
            for (var entry : conditionalEdges.entrySet()) {
                String sourceId = entry.getKey();
                List<Map<String, Object>> condList = entry.getValue();
                Map<String, String> mapping = new LinkedHashMap<>();
                for (Map<String, Object> e : condList) {
                    String cond = (String) ((Map<String, Object>) e.getOrDefault("properties", Map.of())).get("condition");
                    String target = (String) e.get("targetNodeId");
                    mapping.put(cond, target);
                }
                AsyncEdgeAction<WorkflowState> edgeCondition = state -> {
                    // 用 ConditionEvaluator 依次尝试各条件，支持丰富表达式
                    for (var condEntry : mapping.entrySet()) {
                        String condExpr = condEntry.getKey();
                        if (ConditionEvaluator.evaluate(condExpr, state)) {
                            return CompletableFuture.completedFuture(condEntry.getKey());
                        }
                    }
                    // 无一匹配 ——若有 DEFAULT 则用之
                    if (mapping.containsKey("DEFAULT")) {
                        return CompletableFuture.completedFuture("DEFAULT");
                    }
                    // 若 DEFAULT：告警并选第一条路由（容错，非静默失败）
                    String firstKey = mapping.keySet().iterator().next();
                    log.warn("Conditional edge on node '{}': no condition matched and no DEFAULT. "
                            + "Falling back to first route '{}'. Conditions: {}", sourceId, firstKey, mapping.keySet());
                    return CompletableFuture.completedFuture(firstKey);
                };
                graph.addConditionalEdges(sourceId, edgeCondition, mapping);
            }

            // 5. 设置入口边（START -> start-node）
            Optional<Map<String, Object>> startNodeOpt = nodes.stream()
                    .filter(n -> "start-node".equals(n.get("type")))
                    .findFirst();
            if (startNodeOpt.isPresent()) {
                graph.addEdge(START, (String) startNodeOpt.get().get("id"));
            }

            // 6. 设置出口边（end-node -> END）
            for (Map<String, Object> endNode : nodes.stream().filter(n -> "end-node".equals(n.get("type"))).toList()) {
                graph.addEdge((String) endNode.get("id"), END);
            }

            CompileConfig.Builder cfg = CompileConfig.builder();
            BaseCheckpointSaver saver = checkpointSaver.getIfAvailable();
            if (saver != null && sysConfigPort.isYes(SysConfigKeys.CHECKPOINT_ENABLED)) {
                cfg.checkpointSaver(saver);
                // HITL：保留线程检查点，供 confirm/edit/reject resume。
                // 挂起由 InterruptableAction（显式 card-gate 或下一节点自动门闩）判定，
                // 不用 interruptAfter：无卡时也会无条件停下。
                cfg.releaseThread(false);
            }
            return graph.compile(cfg.build());
        } catch (GraphStateException e) {
            throw new IllegalArgumentException("Failed to compile workflow: " + e.getMessage(), e);
        }
    }

    /**
     * 从 executionConfig 提取工作流级默认超时。
     *
     * @param executionConfig 工作流定义中的 executionConfig 。
     * @return 默认超时（毫秒），未配置则为 null
     */
    private Integer extractWorkflowDefaultTimeout(Map<String, Object> executionConfig) {
        if (executionConfig == null) return null;
        Object val = executionConfig.get("defaultTimeoutMs");
        if (val instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return null;
    }

    /** 保证节点属性可写（解析结果可能是不可变 Map）。 */
    private static Map<String, Object> mutableProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(properties);
    }

    /**
     * llm / intent 节点未配 modelCode 时，回填 Agent 默认模型。
     */
    private static void applyDefaultModelCode(String type, Map<String, Object> properties, String defaultModelCode) {
        if (defaultModelCode == null || defaultModelCode.isBlank()) {
            return;
        }
        if (!"llm-node".equals(type) && !"intent-node".equals(type)) {
            return;
        }
        Object existing = properties.get("modelCode");
        if (existing == null || (existing instanceof String s && s.isBlank())) {
            properties.put("modelCode", defaultModelCode);
        }
    }

    /**
     * 从工作流定义 JSON 提取 executionConfig。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractExecutionConfig(GraphSpec workflow) {
        Map<String, Object> definition = parseDefinition(workflow.getDefinition());
        if (definition == null) return null;
        Object cfg = definition.get("executionConfig");
        if (cfg instanceof Map) return (Map<String, Object>) cfg;
        return null;
    }

    /**
     * 将 definition JSON 字符串解析为 Map。
     */
    private Map<String, Object> parseDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(definitionJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse workflow definition JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid workflow definition JSON: " + e.getMessage());
        }
    }
}
