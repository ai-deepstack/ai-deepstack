package org.deepstack.ai.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * AI Agent 工作流引擎健康检查指示器。
 * <p>
 * 报告编译缓存大小与正在运行的执行数；运行数过高或异常时标记 DOWN。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngineHealthIndicator implements HealthIndicator {

    private final WorkflowCompiler workflowCompiler;

    /**
     * 采集引擎健康状态。
     *
     * @return UP/DOWN 及 cache/running 明细
     */
    @Override
    public Health health() {
        try {
            int cacheSize = workflowCompiler.getCompileCacheSize();
            int runningCount = workflowCompiler.getRunningExecutionCount();
            if (runningCount > 100) {
                log.warn("WorkflowEngineHealthIndicator DOWN: runningExecutions={} exceeds threshold", runningCount);
                return Health.down()
                        .withDetail("compileCacheSize", cacheSize)
                        .withDetail("runningExecutions", runningCount)
                        .withDetail("maxCacheSize", 200)
                        .build();
            }
            return Health.up()
                    .withDetail("compileCacheSize", cacheSize)
                    .withDetail("runningExecutions", runningCount)
                    .withDetail("maxCacheSize", 200)
                    .build();
        } catch (Exception e) {
            log.error("WorkflowEngineHealthIndicator DOWN: health check failed: {}", e.getMessage(), e);
            return Health.down(e).build();
        }
    }
}
