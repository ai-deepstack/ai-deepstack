package org.deepstack.ai.agent.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.agent.mapper.AgentAlertEventMapper;
import org.deepstack.ai.agent.mapper.AgentWorkflowRunMapper;
import org.deepstack.ai.agent.model.entity.AgentAlertEvent;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.runtime.WorkflowCompiler;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警扫描与确认：按 sys_config 阈值评估近窗 KPI，去重后落库并可 webhook。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAlertService {

    public static final String RULE_ERROR_RATE = "ERROR_RATE";
    public static final String RULE_P95 = "P95_MS";
    public static final String RULE_HITL_BACKLOG = "HITL_BACKLOG";
    public static final String RULE_RUNNING = "RUNNING";

    private static final String SEVERITY_WARN = "warn";
    private static final String SQL_LIMIT_ONE = "LIMIT 1";
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final AgentAlertEventMapper alertMapper;
    private final AgentWorkflowRunMapper runMapper;
    private final SysConfigPort sysConfigPort;
    private final ObjectProvider<WorkflowCompiler> workflowCompiler;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_CONNECT_TIMEOUT)
            .build();

    /**
     * 扫描并触发告警（若启用）。
     */
    public void scan() {
        if (!sysConfigPort.isYes(SysConfigKeys.ALERT_ENABLED)) {
            log.info("告警扫描跳过: alert.enabled=off");
            return;
        }
        // 阈值与窗口的默认值在 SysConfigFallback.CODED_DEFAULTS / sys_config 种子，这里只读。
        int windowMinutes = Math.max(1, sysConfigPort.getInt(SysConfigKeys.ALERT_WINDOW_MINUTES));
        LocalDateTime from = LocalDateTime.now().minusMinutes(windowMinutes);
        List<AgentWorkflowRun> rows = runMapper.selectList(new LambdaQueryWrapper<AgentWorkflowRun>()
                .ge(AgentWorkflowRun::getCreateTime, from));

        long total = rows.size();
        long failed = rows.stream().filter(r -> GraphRunStatusEnum.FAILED.matches(r.getStatus())).count();
        double errorRate = total == 0 ? 0.0 : (failed * 1.0 / total);
        int p95 = percentile(rows.stream()
                .map(AgentWorkflowRun::getDurationMs)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList(), 0.95);
        long hitlBacklog = runMapper.selectCount(new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getStatus, GraphRunStatusEnum.WAITING_HUMAN.getCode()));
        int running = resolveRunningCount(rows);

        double errorThreshold = sysConfigPort.getDouble(SysConfigKeys.ALERT_ERROR_RATE_THRESHOLD);
        int p95Threshold = sysConfigPort.getInt(SysConfigKeys.ALERT_P95_MS_THRESHOLD);
        int hitlThreshold = sysConfigPort.getInt(SysConfigKeys.ALERT_HITL_BACKLOG_THRESHOLD);
        int runningThreshold = sysConfigPort.getInt(SysConfigKeys.ALERT_RUNNING_THRESHOLD);

        log.info("告警扫描: window={}m, total={}, failed={}, errorRate={}, p95={}, hitl={}, running={}",
                windowMinutes, total, failed, errorRate, p95, hitlBacklog, running);

        if (total > 0 && errorRate >= errorThreshold) {
            maybeFire(RULE_ERROR_RATE, null, SEVERITY_WARN,
                    "错误率超阈值",
                    String.format("近 %d 分钟错误率=%.4f (阈值=%.4f), total=%d, failed=%d",
                            windowMinutes, errorRate, errorThreshold, total, failed),
                    windowMinutes);
        }
        if (p95 >= p95Threshold && p95Threshold > 0) {
            maybeFire(RULE_P95, null, SEVERITY_WARN,
                    "P95 延迟超阈值",
                    String.format("近 %d 分钟 P95=%dms (阈值=%dms)", windowMinutes, p95, p95Threshold),
                    windowMinutes);
        }
        if (hitlBacklog >= hitlThreshold && hitlThreshold > 0) {
            maybeFire(RULE_HITL_BACKLOG, null, SEVERITY_WARN,
                    "HITL 积压超阈值",
                    String.format("WAITING_HUMAN=%d (阈值=%d)", hitlBacklog, hitlThreshold),
                    windowMinutes);
        }
        if (running >= runningThreshold && runningThreshold > 0) {
            maybeFire(RULE_RUNNING, null, SEVERITY_WARN,
                    "在跑数超阈值",
                    String.format("running=%d (阈值=%d)", running, runningThreshold),
                    windowMinutes);
        }
    }

    /**
     * 分页查询告警事件。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 分页结果
     */
    public Page<AgentAlertEvent> page(int pageNum, int pageSize) {
        Page<AgentAlertEvent> page = alertMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AgentAlertEvent>().orderByDesc(AgentAlertEvent::getFiredAt));
        log.info("告警分页: pageNum={}, pageSize={}, total={}", pageNum, pageSize, page.getTotal());
        return page;
    }

    /**
     * 确认告警。
     *
     * @param id 告警 id
     * @return true 表示更新成功
     */
    public boolean acknowledge(Long id) {
        if (id == null) {
            log.warn("告警 ack 跳过: id 为空");
            return false;
        }
        AgentAlertEvent patch = new AgentAlertEvent();
        patch.setId(id);
        patch.setAcknowledged(YesNo.YES.getCode());
        int n = alertMapper.updateById(patch);
        log.info("告警 ack: id={}, updated={}", id, n);
        return n > 0;
    }

    /**
     * 去重后落库并可选 webhook。
     *
     * @param ruleCode      规则码
     * @param agentCode     智能体编码，可为 null
     * @param severity      级别
     * @param title         标题
     * @param detail        详情
     * @param windowMinutes 去重窗口分钟
     */
    private void maybeFire(String ruleCode, String agentCode, String severity,
                           String title, String detail, int windowMinutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        LambdaQueryWrapper<AgentAlertEvent> q = new LambdaQueryWrapper<AgentAlertEvent>()
                .eq(AgentAlertEvent::getRuleCode, ruleCode)
                .ge(AgentAlertEvent::getFiredAt, since)
                .orderByDesc(AgentAlertEvent::getFiredAt)
                .last(SQL_LIMIT_ONE);
        if (StringUtils.hasText(agentCode)) {
            q.eq(AgentAlertEvent::getAgentCode, agentCode);
        } else {
            q.isNull(AgentAlertEvent::getAgentCode);
        }
        AgentAlertEvent latest = alertMapper.selectOne(q);
        if (latest != null) {
            log.info("告警去重跳过: ruleCode={}, agentCode={}, lastId={}", ruleCode, agentCode, latest.getId());
            return;
        }

        AgentAlertEvent event = new AgentAlertEvent();
        event.setRuleCode(ruleCode);
        event.setAgentCode(agentCode);
        event.setSeverity(severity);
        event.setTitle(title);
        event.setDetail(detail);
        event.setFiredAt(LocalDateTime.now());
        event.setAcknowledged(YesNo.NO.getCode());
        alertMapper.insert(event);
        log.warn("告警触发: id={}, ruleCode={}, title={}, detail={}", event.getId(), ruleCode, title, detail);
        postWebhook(event);
    }

    /**
     * 若配置了 webhook-url 则 POST JSON。
     *
     * @param event 告警事件
     */
    private void postWebhook(AgentAlertEvent event) {
        String url = sysConfigPort.getString(SysConfigKeys.ALERT_WEBHOOK_URL);
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", event.getId());
            body.put("ruleCode", event.getRuleCode());
            body.put("agentCode", event.getAgentCode());
            body.put("severity", event.getSeverity());
            body.put("title", event.getTitle());
            body.put("detail", event.getDetail());
            body.put("firedAt", event.getFiredAt() != null ? event.getFiredAt().toString() : null);
            String json = com.alibaba.fastjson2.JSON.toJSONString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("告警 webhook 已发送: id={}, status={}", event.getId(), resp.statusCode());
        } catch (Exception e) {
            log.error("告警 webhook 发送失败: id={}, url={}, err={}", event.getId(), url, e.getMessage(), e);
        }
    }

    /**
     * 解析在跑数：优先 WorkflowCompiler，否则窗口内 RUNNING 行数。
     *
     * @param windowRows 窗口内运行
     * @return 在跑数
     */
    private int resolveRunningCount(List<AgentWorkflowRun> windowRows) {
        WorkflowCompiler compiler = workflowCompiler.getIfAvailable();
        if (compiler != null) {
            try {
                return compiler.getRunningExecutionCount();
            } catch (Exception e) {
                log.warn("读取 WorkflowCompiler 在跑数失败: {}", e.getMessage());
            }
        }
        return (int) windowRows.stream()
                .filter(r -> GraphRunStatusEnum.RUNNING.matches(r.getStatus()))
                .count();
    }

    /**
     * 分位数。
     *
     * @param sorted 升序列表
     * @param p      分位
     * @return 值
     */
    private static int percentile(List<Integer> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

}
