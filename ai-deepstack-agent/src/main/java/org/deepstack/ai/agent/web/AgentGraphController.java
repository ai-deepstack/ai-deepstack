package org.deepstack.ai.agent.web;

import org.deepstack.ai.agent.graph.AgentGraphService;
import org.deepstack.ai.agent.graph.AgentGraphTestService;
import org.deepstack.ai.agent.graph.NodeTypeRegistry;
import org.deepstack.ai.agent.model.entity.AiAgentGraphVersion;
import org.deepstack.ai.agent.model.dto.response.AgentGraphResponse;
import org.deepstack.ai.agent.model.dto.response.NodeTypeResponse;
import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.engine.GraphSpec;
import org.deepstack.ai.kernel.enums.common.EnabledStatusEnum;
import org.deepstack.ai.kernel.model.Response;
import org.deepstack.ai.kernel.observability.GraphDefinitionKeys;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体编排图 API：图定义读写、试跑、HITL resume、运行查询与取消。
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentGraphController {

    private final AgentGraphService agentGraphService;
    private final AgentGraphTestService agentGraphTestService;
    private final NodeTypeRegistry nodeTypeRegistry;

    /**
     * 读取智能体当前图定义。
     *
     * @param id 智能体主键
     * @return 图响应；无定义时 data 为 null
     */
    @GetMapping("/{id}/graph")
    public Response<AgentGraphResponse> getGraph(@PathVariable("id") Long id) {
        log.info("API getGraph: agentId={}", id);
        GraphSpec entity = agentGraphService.getGraph(id);
        return Response.success(entity != null ? toResponse(entity) : null);
    }

    /**
     * 更新智能体图定义（可能自动将编排模式切到 GRAPH）。
     *
     * @param id         智能体主键
     * @param definition 图定义 JSON 对象
     */
    @PutMapping("/{id}/graph")
    public Response<Void> updateGraph(@PathVariable("id") Long id, @RequestBody Map<String, Object> definition) {
        log.info("API updateGraph: agentId={}", id);
        agentGraphService.updateGraph(id, definition);
        return Response.success();
    }

    /**
     * 发布草稿图：复制到 published，递增版本并写入历史。
     *
     * @param id   智能体主键
     * @param body 可选 body，支持 {@code remark}
     * @return 新的已发布版本号
     */
    @PostMapping("/{id}/graph/publish")
    public Response<Integer> publishGraph(@PathVariable("id") Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null && body.get("remark") != null ? String.valueOf(body.get("remark")) : null;
        log.info("API publishGraph: agentId={}, remark={}", id, remark);
        return Response.success(agentGraphService.publish(id, remark));
    }

    /**
     * 列出已发布图版本历史（版本号降序）。
     *
     * @param id 智能体主键
     * @return 版本摘要列表
     */
    @GetMapping("/{id}/graph/versions")
    public Response<List<Map<String, Object>>> listGraphVersions(@PathVariable("id") Long id) {
        log.info("API listGraphVersions: agentId={}", id);
        List<Map<String, Object>> rows = agentGraphService.listVersions(id).stream()
                .map(this::toVersionView)
                .toList();
        return Response.success(rows);
    }

    /**
     * 回滚到指定历史版本（同步 published 与草稿）。
     *
     * @param id      智能体主键
     * @param version 目标版本号
     */
    @PostMapping("/{id}/graph/rollback/{version}")
    public Response<Void> rollbackGraph(@PathVariable("id") Long id, @PathVariable("version") Integer version) {
        log.info("API rollbackGraph: agentId={}, version={}", id, version);
        agentGraphService.rollback(id, version);
        return Response.success();
    }

    /**
     * 将节点/边片段合并进草稿（节点 id 重新生成）。
     *
     * @param id   智能体主键
     * @param body {@code nodes} / {@code edges} 列表
     */
    @PostMapping("/{id}/graph/insert-fragment")
    @SuppressWarnings("unchecked")
    public Response<Void> insertFragment(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> nodes = body != null && body.get(GraphDefinitionKeys.NODES) instanceof List
                ? (List<Map<String, Object>>) body.get(GraphDefinitionKeys.NODES) : List.of();
        List<Map<String, Object>> edges = body != null && body.get(GraphDefinitionKeys.EDGES) instanceof List
                ? (List<Map<String, Object>>) body.get(GraphDefinitionKeys.EDGES) : List.of();
        log.info("API insertFragment: agentId={}, nodes={}, edges={}", id, nodes.size(), edges.size());
        agentGraphService.insertFragment(id, nodes, edges);
        return Response.success();
    }

    /**
     * 同步试跑图（阻塞等待完成或超时）。
     *
     * @param id  智能体主键
     * @param req 试跑请求（须含 message）
     * @return 运行结果
     */
    @PostMapping("/{id}/graph/test")
    public Response<GraphRunResponse> testGraph(@PathVariable("id") Long id, @RequestBody GraphRunRequest req) {
        log.info("API testGraph: agentId={}, conversationId={}",
                id, req != null ? req.getConversationId() : null);
        return Response.success(agentGraphTestService.test(id, req));
    }

    /**
     * HITL：从 checkpoint 恢复（请求须带 {@code threadId}，可选 {@code resumeUpdates}）。
     *
     * @param id  智能体主键
     * @param req 恢复请求
     * @return 恢复后的运行结果
     */
    @PostMapping("/{id}/graph/resume")
    public Response<GraphRunResponse> resumeGraph(@PathVariable("id") Long id, @RequestBody GraphRunRequest req) {
        log.info("API resumeGraph: agentId={}, threadId={}",
                id, req != null ? req.getThreadId() : null);
        return Response.success(agentGraphTestService.resume(id, req));
    }

    /**
     * 异步启动试跑，立即返回 runId。
     *
     * @param id  智能体主键
     * @param req 试跑请求
     * @return 含 runId 的 map
     */
    @PostMapping("/{id}/graph/test/start")
    public Response<Map<String, Object>> startTestGraph(@PathVariable("id") Long id, @RequestBody GraphRunRequest req) {
        log.info("API startTestGraph: agentId={}", id);
        Long runId = agentGraphTestService.startAsync(id, req);
        return Response.success(Map.of("runId", runId));
    }

    /**
     * 查询试跑运行状态与结果。
     *
     * @param runId 运行记录主键
     * @return 运行响应
     */
    @GetMapping("/runs/{runId}")
    public Response<GraphRunResponse> getRun(@PathVariable("runId") Long runId) {
        log.info("API getRun: runId={}", runId);
        return Response.success(agentGraphTestService.getRun(runId));
    }

    /**
     * 列出平台注册的节点类型（画布用）。
     *
     * @return 节点类型列表
     */
    @GetMapping("/node-types")
    public Response<List<NodeTypeResponse>> nodeTypes() {
        log.debug("API nodeTypes");
        return Response.success(nodeTypeRegistry.buildAll());
    }

    /**
     * 取消仍在 RUNNING 的试跑。
     *
     * @param runId 运行记录主键
     * @return true 表示已发起取消；非 RUNNING 时为 false
     */
    @PostMapping("/runs/{runId}/cancel")
    public Response<Boolean> cancelRun(@PathVariable("runId") Long runId) {
        log.info("API cancelRun: runId={}", runId);
        return Response.success(agentGraphTestService.cancelRun(runId));
    }

    /**
     * 版本历史行 → API 视图（不含完整 definition，避免列表过大）。
     *
     * @param v 版本实体
     * @return 摘要 Map
     */
    private Map<String, Object> toVersionView(AiAgentGraphVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("agentId", v.getAgentId());
        m.put("version", v.getVersion());
        m.put("remark", v.getRemark());
        m.put("creator", v.getCreator());
        m.put("createTime", v.getCreateTime());
        String def = v.getDefinition();
        m.put("definitionLength", def != null ? def.length() : 0);
        return m;
    }

    /**
     * GraphSpec → API 响应（definition 反序列化为 Map）。
     *
     * @param e 图规格
     * @return 响应 DTO
     */
    private AgentGraphResponse toResponse(GraphSpec e) {
        AgentGraphResponse r = new AgentGraphResponse();
        r.setId(e.getId());
        r.setWorkflowCode(e.getCode());
        r.setWorkflowName(e.getName());
        r.setDescription(e.getDescription());
        r.setDefinition(parseDefinition(e.getDefinition()));
        r.setWorkflowType(e.getGraphType());
        r.setVersion(e.getVersion());
        r.setEnabled(e.getEnabled());
        r.setEnabledName(EnabledStatusEnum.labelOf(e.getEnabled()));
        return r;
    }

    /**
     * 解析图定义 JSON；失败时记 warn 并返回 null。
     *
     * @param json 图定义字符串
     * @return Map；空或解析失败为 null
     */
    private Map<String, Object> parseDefinition(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析 definition JSON 失败: {}", e.getMessage());
            return null;
        }
    }
}
