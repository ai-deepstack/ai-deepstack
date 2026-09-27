package org.deepstack.ai.agent.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 告警定时扫描（每 5 分钟）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentAlertTask {

    private final AgentAlertService agentAlertService;

    /**
     * 每 5 分钟执行一次告警扫描。
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void scanAlerts() {
        log.info("告警定时任务触发");
        try {
            agentAlertService.scan();
        } catch (Exception e) {
            log.error("告警定时任务失败: {}", e.getMessage(), e);
        }
    }
}
