package org.deepstack.ai.agent.observability;

import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运行概览聚合（从落库行生成 KPI / Top / 趋势）。
 * <p>包内工具类，由 {@link AgentRunService#overview} 调用。</p>
 */
@Slf4j
final class AgentRunOverviewAssembler {

    private static final int TOP_N = 10;
    private static final int RECENT_N = 20;

    /** 禁止实例化。 */
    private AgentRunOverviewAssembler() {
    }

    /**
     * 聚合窗口内运行行。
     *
     * @param rows       时间窗内全部运行
     * @param hours      回溯小时数（写入结果）
     * @param from       窗口起点
     * @param toListView 行 → 列表摘要映射
     * @return overview 响应 map（kpi / failTopAgents / slowRuns / trend 等）
     */
    static Map<String, Object> assemble(List<AgentWorkflowRun> rows, int hours, LocalDateTime from,
                                        Function<AgentWorkflowRun, Map<String, Object>> toListView) {
        long total = rows.size();
        long success = countByStatus(rows, GraphRunStatusEnum.SUCCESS);
        long failed = countByStatus(rows, GraphRunStatusEnum.FAILED);
        long waiting = countByStatus(rows, GraphRunStatusEnum.WAITING_HUMAN);
        long running = countByStatus(rows, GraphRunStatusEnum.RUNNING);

        List<Integer> durations = rows.stream()
                .map(AgentWorkflowRun::getDurationMs)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        long tokenSum = rows.stream()
                .map(AgentWorkflowRun::getTotalTokens)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", total);
        kpi.put("success", success);
        kpi.put("failed", failed);
        kpi.put("waitingHuman", waiting);
        kpi.put("running", running);
        kpi.put("successRate", total == 0 ? 0.0 : (success * 1.0 / total));
        kpi.put("p50DurationMs", percentile(durations, 0.50));
        kpi.put("p95DurationMs", percentile(durations, 0.95));
        kpi.put("avgDurationMs", durations.isEmpty() ? 0 :
                (int) durations.stream().mapToInt(Integer::intValue).average().orElse(0));
        kpi.put("totalTokens", tokenSum);

        Map<String, Long> failByAgent = rows.stream()
                .filter(r -> GraphRunStatusEnum.FAILED.matches(r.getStatus()))
                .collect(Collectors.groupingBy(
                        r -> StringUtils.hasText(r.getAgentCode()) ? r.getAgentCode() : String.valueOf(r.getAgentId()),
                        Collectors.counting()));
        List<Map<String, Object>> failTop = failByAgent.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("agentCode", e.getKey());
                    m.put("failed", e.getValue());
                    return m;
                })
                .toList();

        Map<String, Long> errorCodes = rows.stream()
                .filter(r -> StringUtils.hasText(r.getErrorCode()))
                .collect(Collectors.groupingBy(AgentWorkflowRun::getErrorCode, Collectors.counting()));

        List<Map<String, Object>> slow = rows.stream()
                .filter(r -> r.getDurationMs() != null)
                .sorted(Comparator.comparing(AgentWorkflowRun::getDurationMs).reversed())
                .limit(TOP_N)
                .map(toListView)
                .toList();

        List<Map<String, Object>> recentFailed = rows.stream()
                .filter(r -> GraphRunStatusEnum.FAILED.matches(r.getStatus())
                        || GraphRunStatusEnum.WAITING_HUMAN.matches(r.getStatus()))
                .sorted(Comparator.comparing(AgentWorkflowRun::getId).reversed())
                .limit(RECENT_N)
                .map(toListView)
                .toList();

        Map<String, long[]> trendBucket = new HashMap<>();
        for (AgentWorkflowRun r : rows) {
            if (r.getCreateTime() == null) {
                continue;
            }
            String key = r.getCreateTime().truncatedTo(ChronoUnit.HOURS).toString();
            long[] arr = trendBucket.computeIfAbsent(key, k -> new long[2]);
            arr[0]++;
            if (GraphRunStatusEnum.FAILED.matches(r.getStatus())) {
                arr[1]++;
            }
        }
        List<Map<String, Object>> trend = trendBucket.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("hour", e.getKey());
                    m.put("total", e.getValue()[0]);
                    m.put("failed", e.getValue()[1]);
                    return m;
                })
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hours", hours);
        out.put("from", from.toString());
        out.put("kpi", kpi);
        out.put("failTopAgents", failTop);
        out.put("errorCodes", errorCodes);
        out.put("slowRuns", slow);
        out.put("recentFailedOrWaiting", recentFailed);
        out.put("trend", trend);
        log.debug("overview assemble: hours={}, total={}, failed={}, successRate={}",
                hours, total, failed, kpi.get("successRate"));
        return out;
    }

    /**
     * 按状态码计数。
     *
     * @param rows   运行行
     * @param status 目标状态
     * @return 匹配条数
     */
    private static long countByStatus(List<AgentWorkflowRun> rows, GraphRunStatusEnum status) {
        return rows.stream().filter(r -> status.matches(r.getStatus())).count();
    }

    /**
     * 已排序耗时列表的分位数（ceil 索引）。
     *
     * @param sorted 升序 durationMs
     * @param p      分位，如 0.50 / 0.95
     * @return 分位值；空列表返回 0
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
