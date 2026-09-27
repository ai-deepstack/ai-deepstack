package org.deepstack.ai.agent.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.deepstack.ai.agent.mapper.AgentWorkflowRunMapper;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.agent.service.AiAgentService;
import org.deepstack.ai.kernel.enums.agent.AgentRunErrorCode;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.runtime.GraphRuntime;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HITL 超时扫描：将超时仍挂起的 WAITING_HUMAN 标记为 FAILED(HITL_TIMEOUT)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HitlTimeoutTask {

    private static final int BATCH_LIMIT = 100;
    private static final int DEFAULT_TIMEOUT_MINUTES = 60;

    private final AgentWorkflowRunMapper runMapper;
    private final AgentRunService agentRunService;
    private final AiAgentService aiAgentService;
    private final SysConfigPort sysConfigPort;
    private final ObjectProvider<BaseCheckpointSaver> checkpointSaver;
    private final ObjectProvider<GraphRuntime> graphRuntime;

    /**
     * 每 2 分钟扫描一批超时 HITL。
     */
    @Scheduled(cron = "0 */2 * * * ?")
    public void expireHitlWaiting() {
        LocalDateTime now = LocalDateTime.now();
        int globalMinutes = sysConfigPort.getInt(SysConfigKeys.HITL_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES);
        log.info("HITL 超时扫描开始: now={}, globalTimeoutMinutes={}", now, globalMinutes);

        List<AgentWorkflowRun> waiting = runMapper.selectList(new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getStatus, GraphRunStatusEnum.WAITING_HUMAN.getCode())
                .isNotNull(AgentWorkflowRun::getHitlSuspendedAt)
                .orderByAsc(AgentWorkflowRun::getHitlSuspendedAt)
                .last("LIMIT " + BATCH_LIMIT));

        int expired = 0;
        for (AgentWorkflowRun run : waiting) {
            try {
                int minutes = resolveTimeoutMinutes(run, globalMinutes);
                LocalDateTime deadline = run.getHitlSuspendedAt().plusMinutes(minutes);
                if (deadline.isAfter(now)) {
                    continue;
                }
                log.warn("HITL 超时: runId={}, agentCode={}, threadId={}, suspendedAt={}, timeoutMinutes={}",
                        run.getId(), run.getAgentCode(), run.getThreadId(),
                        run.getHitlSuspendedAt(), minutes);
                agentRunService.complete(run, GraphRunStatusEnum.FAILED, null, null, null, 0, 0,
                        AgentRunErrorCode.HITL_TIMEOUT.getLabel(), AgentRunErrorCode.HITL_TIMEOUT);
                releaseCheckpointBestEffort(run);
                if (run.getId() != null) {
                    GraphRuntime runtime = graphRuntime.getIfAvailable();
                    if (runtime != null) {
                        try {
                            runtime.cancelExecution(run.getId());
                        } catch (Exception e) {
                            log.warn("HITL 超时取消执行失败: runId={}, err={}", run.getId(), e.getMessage());
                        }
                    }
                }
                expired++;
            } catch (Exception e) {
                log.error("HITL 超时处理失败: runId={}, err={}", run.getId(), e.getMessage(), e);
            }
        }
        log.info("HITL 超时扫描结束: scanned={}, expired={}", waiting.size(), expired);
    }

    /**
     * 解析超时分钟：智能体配置优先，否则全局。
     *
     * @param run           运行行
     * @param globalMinutes 全局默认
     * @return 超时分钟（至少 1）
     */
    private int resolveTimeoutMinutes(AgentWorkflowRun run, int globalMinutes) {
        if (run.getAgentId() != null) {
            AiAgent agent = aiAgentService.getById(run.getAgentId());
            if (agent != null && agent.getHitlTimeoutMinutes() != null && agent.getHitlTimeoutMinutes() > 0) {
                return agent.getHitlTimeoutMinutes();
            }
        }
        if (StringUtils.hasText(run.getAgentCode())) {
            AiAgent agent = aiAgentService.getByAgentCode(run.getAgentCode());
            if (agent != null && agent.getHitlTimeoutMinutes() != null && agent.getHitlTimeoutMinutes() > 0) {
                return agent.getHitlTimeoutMinutes();
            }
        }
        return Math.max(1, globalMinutes);
    }

    /**
     * 尽力释放 checkpoint；无 delete/release API 时仅 warn。
     *
     * @param run 超时运行
     */
    private void releaseCheckpointBestEffort(AgentWorkflowRun run) {
        if (!StringUtils.hasText(run.getThreadId())) {
            log.warn("HITL checkpoint release 跳过: 无 threadId, runId={}", run.getId());
            return;
        }
        BaseCheckpointSaver saver = checkpointSaver.getIfAvailable();
        if (saver == null) {
            log.warn("HITL checkpoint release 跳过: 无 CheckpointSaver, threadId={}", run.getThreadId());
            return;
        }
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(run.getThreadId().trim())
                    .build();
            Method delete = findReleaseMethod(saver.getClass());
            if (delete != null) {
                delete.invoke(saver, config);
                log.info("HITL checkpoint released: threadId={}, method={}", run.getThreadId(), delete.getName());
            } else {
                log.warn("HITL checkpoint release 为 best-effort：Saver 无 delete/release(threadId), threadId={}, saver={}",
                        run.getThreadId(), saver.getClass().getName());
            }
        } catch (Exception e) {
            log.warn("HITL checkpoint release 失败(best-effort): threadId={}, err={}",
                    run.getThreadId(), e.getMessage());
        }
    }

    /**
     * 查找 delete/release(RunnableConfig) 方法。
     *
     * @param clazz Saver 类型
     * @return 方法或 null
     */
    private static Method findReleaseMethod(Class<?> clazz) {
        for (String name : List.of("delete", "release", "clear", "remove")) {
            try {
                return clazz.getMethod(name, RunnableConfig.class);
            } catch (NoSuchMethodException ignored) {
                // continue
            }
        }
        return null;
    }
}
