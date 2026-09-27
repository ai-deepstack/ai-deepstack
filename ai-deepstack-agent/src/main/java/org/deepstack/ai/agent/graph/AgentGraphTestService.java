package org.deepstack.ai.agent.graph;

import org.deepstack.ai.agent.mapper.AgentWorkflowRunMapper;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.agent.observability.AgentRunService;
import org.deepstack.ai.agent.service.AgentToolBindingService;
import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.engine.GraphSpec;
import org.deepstack.ai.engine.TraceConfig;
import org.deepstack.ai.kernel.observability.AgentRunCancelReasons;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.runtime.GraphRuntime;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 图试跑：编排侧加工 toolCodes 等注入数据，再交给 {@link GraphRuntime}。
 */
@Slf4j
@Service
public class AgentGraphTestService {

    /** 同步试跑轮询间隔。 */
    private static final long TEST_POLL_INTERVAL_MS = 200L;
    /** 约 5 分钟：1500 × 200ms。 */
    private static final int TEST_MAX_POLLS = 1500;

    private final AgentGraphService agentGraphService;
    private final AgentToolBindingService agentToolBindingService;
    private final AgentWorkflowRunMapper agentWorkflowRunMapper;
    private final AgentRunService agentRunService;
    private final GraphRuntime graphRuntime;
    private final Executor workflowExecutor;

    public AgentGraphTestService(
            AgentGraphService agentGraphService,
            AgentToolBindingService agentToolBindingService,
            AgentWorkflowRunMapper agentWorkflowRunMapper,
            AgentRunService agentRunService,
            GraphRuntime graphRuntime,
            @Qualifier("workflowExecutor") Executor workflowExecutor) {
        this.agentGraphService = agentGraphService;
        this.agentToolBindingService = agentToolBindingService;
        this.agentWorkflowRunMapper = agentWorkflowRunMapper;
        this.agentRunService = agentRunService;
        this.graphRuntime = graphRuntime;
        this.workflowExecutor = workflowExecutor;
    }

    /**
     * 同步试跑：内部异步启动后轮询直至结束或超时（约 5 分钟）。
     */
    public GraphRunResponse test(Long agentId, GraphRunRequest req) {
        Long runId = startAsync(agentId, req);
        int spins = 0;
        do {
            GraphRunResponse running = getRun(runId);
            if (running != null && !GraphRunStatusEnum.RUNNING.matches(running.getStatus())) {
                return running;
            }
            try {
                Thread.sleep(TEST_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelRun(runId);
                GraphRunResponse cancelled = new GraphRunResponse();
                cancelled.setRunId(runId);
                cancelled.applyStatus(GraphRunStatusEnum.CANCELLED);
                cancelled.setErrorMessage("Interrupted");
                return cancelled;
            }
            spins++;
        } while (spins < TEST_MAX_POLLS);
        GraphRunResponse timeout = new GraphRunResponse();
        timeout.setRunId(runId);
        timeout.applyStatus(GraphRunStatusEnum.FAILED);
        timeout.setErrorMessage("Test timed out waiting for completion");
        return timeout;
    }

    /**
     * 异步启动试跑：落 RUNNING 记录后提交线程池执行，立即返回 runId。
     */
    public Long startAsync(Long agentId, GraphRunRequest req) {
        if (req == null || !StringUtils.hasText(req.getMessage())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "测试消息不能为空");
        }
        GraphSpec workflow = agentGraphService.getGraph(agentId);
        if (workflow == null || !StringUtils.hasText(workflow.getDefinition())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "Agent has no graph definition: id=" + agentId);
        }
        req.setToolCodes(agentToolBindingService.listEnabledToolCodes(agentId));
        if (!StringUtils.hasText(req.getAgentCode())) {
            req.setAgentCode(workflow.getCode());
        }

        AgentWorkflowRun runLog = agentRunService.start(
                agentId, workflow.getCode(), OrchestrateModeEnum.GRAPH,
                req.getConversationId(), req.getMessage(),
                req.getThreadId(), workflow.getVersion(), workflow.getDefinition());
        Long runId = runLog.getId();

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                GraphRunResponse result = graphRuntime.execute(workflow, req);
                int duration = (int) (System.currentTimeMillis() - startTime);
                GraphRunStatusEnum st = GraphRunStatusEnum.ofRequired(result.getStatus());
                // 仅当仍为 RUNNING 时回写，避免与 cancel 竞态覆盖
                AgentWorkflowRun fresh = agentWorkflowRunMapper.selectById(runId);
                if (fresh != null && GraphRunStatusEnum.RUNNING.matches(fresh.getStatus())) {
                    agentRunService.complete(fresh, st, result.getResult(), result.getNodeExecutions(),
                            null, result.getTotalTokens(), duration, result.getErrorMessage(), null);
                }
            } catch (Exception e) {
                int duration = (int) (System.currentTimeMillis() - startTime);
                log.error("graph test failed: agentId={}, runId={}", agentId, runId, e);
                AgentWorkflowRun fresh = agentWorkflowRunMapper.selectById(runId);
                if (fresh != null && GraphRunStatusEnum.RUNNING.matches(fresh.getStatus())) {
                    agentRunService.completeFailed(fresh, e, duration);
                }
            }
        }, workflowExecutor).whenComplete((v, ex) -> graphRuntime.cancelExecution(runId));

        graphRuntime.registerRunningExecution(runId, future);
        return runId;
    }

    /**
     * HITL：从 checkpoint 按 threadId 恢复图执行。
     */
    public GraphRunResponse resume(Long agentId, GraphRunRequest req) {
        if (req == null || !StringUtils.hasText(req.getThreadId())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "threadId 不能为空");
        }
        GraphSpec workflow = agentGraphService.getGraph(agentId);
        if (workflow == null || !StringUtils.hasText(workflow.getDefinition())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "Agent has no graph definition: id=" + agentId);
        }
        req.setToolCodes(agentToolBindingService.listEnabledToolCodes(agentId));
        String agentCode = StringUtils.hasText(req.getAgentCode()) ? req.getAgentCode() : workflow.getCode();
        log.info("graph resume: agentId={}, threadId={}, agentCode={}", agentId, req.getThreadId(), agentCode);

        try {
            return agentRunService.runHitlResume(req.getThreadId(), agentCode,
                    () -> graphRuntime.resume(
                            workflow, req.getThreadId(), req.getResumeUpdates(), req, new TraceConfig()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(),
                    "graph resume failed: " + e.getMessage());
        }
    }

    /** 返回 Run。 */
    public GraphRunResponse getRun(Long runId) {
        AgentWorkflowRun runLog = agentWorkflowRunMapper.selectById(runId);
        if (runLog == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "Workflow run not found: id=" + runId);
        }
        GraphRunResponse resp = new GraphRunResponse();
        resp.setRunId(runId);
        GraphRunStatusEnum st = GraphRunStatusEnum.ofRequired(runLog.getStatus());
        resp.applyStatus(st);
        resp.setResult(runLog.getResult());
        resp.setErrorMessage(runLog.getErrorMessage());
        resp.setTotalTokens(runLog.getTotalTokens());
        resp.setTotalDurationMs(runLog.getDurationMs());
        resp.setNodeExecutions(parseNodeExecutions(runLog.getNodeExecutions()));
        return resp;
    }

    /** 取消仍在运行的图测试。 */
    public boolean cancelRun(Long runId) {
        AgentWorkflowRun runLog = agentWorkflowRunMapper.selectById(runId);
        if (runLog == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "Workflow run not found: id=" + runId);
        }
        if (!GraphRunStatusEnum.RUNNING.matches(runLog.getStatus())) {
            return false;
        }
        boolean cancelled = graphRuntime.cancelExecution(runId);
        agentRunService.markCancelled(runId, AgentRunCancelReasons.USER);
        log.info("Workflow run cancelled: runId={}, compilerCancelled={}", runId, cancelled);
        return true;
    }

    /** 解析节点执行 JSON。 */
    private List<Map<String, Object>> parseNodeExecutions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.parseObject(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("parseNodeExecutions failed: err={}", e.getMessage());
            return List.of();
        }
    }
}
