package org.deepstack.ai.agent.web;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.agent.observability.AgentRunService;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import org.deepstack.ai.runtime.WorkflowCompiler;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 智能体运行可观测 API：概览 / 列表 / 详情 / JSONL 导出。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent-runs")
@RequiredArgsConstructor
public class AgentRunObservabilityController {

    private static final int DEFAULT_OVERVIEW_HOURS = 24;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int EXPORT_PAGE_SIZE = 200;

    private final AgentRunService agentRunService;
    private final WorkflowCompiler workflowCompiler;

    /**
     * 运营概览：KPI、失败 Top、慢请求、趋势。
     */
    @GetMapping("/overview")
    public Response<Map<String, Object>> overview(
            @RequestParam(defaultValue = "" + DEFAULT_OVERVIEW_HOURS) int hours,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Integer orchestrateMode) {
        log.info("API agent-runs overview: hours={}, agentId={}, mode={}", hours, agentId, orchestrateMode);
        Map<String, Object> data = agentRunService.overview(hours, agentId, orchestrateMode);
        Map<String, Object> health = new LinkedHashMap<>();
        try {
            health.put("compileCacheSize", workflowCompiler.getCompileCacheSize());
            health.put("runningExecutions", workflowCompiler.getRunningExecutionCount());
        } catch (Exception e) {
            log.warn("overview health 采集失败: {}", e.getMessage());
            health.put("error", e.getMessage());
        }
        data.put("engineHealth", health);
        return Response.success(data);
    }

    /**
     * 运行分页列表。
     */
    @GetMapping
    public Response<PageInfo<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String agentCode,
            @RequestParam(required = false) Integer orchestrateMode,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        log.info("API agent-runs page: pageNum={}, pageSize={}, agentCode={}, status={}, traceId={}",
                pageNum, pageSize, agentCode, status, traceId);
        Page<AgentWorkflowRun> page = agentRunService.page(
                pageNum, pageSize, agentId, agentCode, orchestrateMode, status, traceId, from, to);
        return Response.success(PageInfoUtils.of(page, agentRunService::toListView));
    }

    /**
     * 按条件流式导出运行记录为 JSONL（NDJSON）。
     *
     * @param agentCode 智能体编码过滤
     * @param agentId   智能体主键过滤
     * @param from      创建时间起
     * @param to        创建时间止
     * @return 附件流
     */
    @GetMapping(value = "/export", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String agentCode,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        log.info("API agent-runs export: agentCode={}, agentId={}, from={}, to={}",
                agentCode, agentId, from, to);
        AtomicLong exported = new AtomicLong(0);
        StreamingResponseBody body = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                int pageNum = 1;
                while (true) {
                    Page<AgentWorkflowRun> page = agentRunService.page(
                            pageNum, EXPORT_PAGE_SIZE, agentId, agentCode, null, null, null, from, to);
                    if (page.getRecords() == null || page.getRecords().isEmpty()) {
                        break;
                    }
                    for (AgentWorkflowRun run : page.getRecords()) {
                        writer.write(JSON.toJSONString(toExportLine(run),
                                com.alibaba.fastjson2.JSONWriter.Feature.WriteLongAsString));
                        writer.write('\n');
                        exported.incrementAndGet();
                    }
                    if (pageNum >= page.getPages()) {
                        break;
                    }
                    pageNum++;
                }
                writer.flush();
            }
            log.info("API agent-runs export done: count={}, agentCode={}, agentId={}",
                    exported.get(), agentCode, agentId);
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=agent-runs-export.jsonl")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(body);
    }

    /**
     * 单次运行详情（含 nodeExecutions / stages JSON 原文）。
     */
    @GetMapping("/{id}")
    public Response<Map<String, Object>> detail(@PathVariable Long id) {
        log.info("API agent-runs detail: id={}", id);
        AgentWorkflowRun run = agentRunService.getById(id);
        if (run == null) {
            log.warn("API agent-runs detail not found: id={}", id);
            return Response.fail(CommonErrorCode.NOT_FOUND.getCode(), "运行记录不存在: " + id);
        }
        log.info("API agent-runs detail ok: id={}, agentCode={}, status={}, traceId={}",
                id, run.getAgentCode(), run.getStatus(), run.getTraceId());
        return Response.success(agentRunService.toDetailView(run));
    }

    /**
     * 组装导出行字段。
     *
     * @param r 运行实体
     * @return 导出 Map
     */
    private Map<String, Object> toExportLine(AgentWorkflowRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.getId());
        m.put("traceId", r.getTraceId());
        m.put("userMessage", r.getUserMessage());
        m.put("result", r.getResult());
        m.put("status", r.getStatus());
        m.put("statusName", GraphRunStatusEnum.labelOf(r.getStatus()));
        m.put("totalTokens", r.getTotalTokens());
        m.put("durationMs", r.getDurationMs());
        m.put("graphVersion", r.getGraphVersion());
        m.put("definitionSnapshot", r.getDefinitionSnapshot());
        m.put("nodeExecutions", r.getNodeExecutions());
        m.put("errorCode", r.getErrorCode());
        m.put("agentCode", r.getAgentCode());
        m.put("createTime", r.getCreateTime());
        return m;
    }
}
