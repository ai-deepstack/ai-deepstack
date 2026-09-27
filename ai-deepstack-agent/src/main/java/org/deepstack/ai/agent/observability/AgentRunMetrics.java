package org.deepstack.ai.agent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.kernel.enums.agent.AgentRunErrorCode;
import org.deepstack.ai.kernel.observability.AgentRunMetricNames;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 智能体运行 Micrometer 指标（O5）。
 * <p>
 * 指标名见 {@link AgentRunMetricNames}：run.count / duration / error、tokens、hitl.wait。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunMetrics {

    private final MeterRegistry meterRegistry;

    /**
     * 记录一次运行结束：计数、耗时；有业务错误码时记 error；有 token 时累加。
     *
     * @param agentCode   智能体编码
     * @param mode        编排模式名（如 CHAT / GRAPH）
     * @param status      状态名（如 SUCCESS / FAILED）
     * @param errorCode   错误码字符串；NONE 或空不记 error 计数
     * @param durationMs  耗时毫秒
     * @param totalTokens token 合计，可为 null
     */
    public void recordRun(String agentCode, String mode, String status, String errorCode,
                          long durationMs, Integer totalTokens) {
        String agent = blankToUnknown(agentCode);
        String m = blankToUnknown(mode);
        String st = blankToUnknown(status);
        Counter.builder(AgentRunMetricNames.RUN_COUNT)
                .tag(AgentRunMetricNames.TAG_AGENT, agent)
                .tag(AgentRunMetricNames.TAG_MODE, m)
                .tag(AgentRunMetricNames.TAG_STATUS, st)
                .register(meterRegistry)
                .increment();
        Timer.builder(AgentRunMetricNames.RUN_DURATION)
                .tag(AgentRunMetricNames.TAG_AGENT, agent)
                .tag(AgentRunMetricNames.TAG_MODE, m)
                .tag(AgentRunMetricNames.TAG_STATUS, st)
                .register(meterRegistry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
        if (errorCode != null && !errorCode.isBlank()
                && !AgentRunErrorCode.NONE.getCode().equalsIgnoreCase(errorCode)) {
            Counter.builder(AgentRunMetricNames.RUN_ERROR)
                    .tag(AgentRunMetricNames.TAG_AGENT, agent)
                    .tag(AgentRunMetricNames.TAG_MODE, m)
                    .tag(AgentRunMetricNames.TAG_ERROR, errorCode)
                    .register(meterRegistry)
                    .increment();
        }
        if (totalTokens != null && totalTokens > 0) {
            Counter.builder(AgentRunMetricNames.TOKENS)
                    .tag(AgentRunMetricNames.TAG_AGENT, agent)
                    .tag(AgentRunMetricNames.TAG_MODE, m)
                    .register(meterRegistry)
                    .increment(totalTokens);
        }
        log.debug("AgentRunMetrics recordRun: agent={}, mode={}, status={}, error={}, durationMs={}, tokens={}",
                agent, m, st, errorCode, durationMs, totalTokens);
    }

    /**
     * 记录 HITL 等待时长。
     *
     * @param agentCode 智能体编码
     * @param waitMs    等待毫秒
     */
    public void recordHitlWait(String agentCode, long waitMs) {
        Timer.builder(AgentRunMetricNames.HITL_WAIT)
                .tag(AgentRunMetricNames.TAG_AGENT, blankToUnknown(agentCode))
                .register(meterRegistry)
                .record(Math.max(0, waitMs), TimeUnit.MILLISECONDS);
        log.info("AgentRunMetrics HITL wait: agent={}, waitMs={}", agentCode, waitMs);
    }

    /**
     * 空串归一为 {@link AgentRunMetricNames#UNKNOWN}，避免 Micrometer 空 tag。
     *
     * @param v 原始标签值
     * @return 非空标签
     */
    private static String blankToUnknown(String v) {
        return v == null || v.isBlank() ? AgentRunMetricNames.UNKNOWN : v.trim();
    }
}
