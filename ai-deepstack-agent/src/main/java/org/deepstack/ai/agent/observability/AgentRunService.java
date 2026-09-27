package org.deepstack.ai.agent.observability;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.agent.mapper.AgentWorkflowRunMapper;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.kernel.enums.agent.AgentRunErrorCode;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum;
import org.deepstack.ai.kernel.observability.AgentRunMetricNames;
import org.deepstack.ai.kernel.observability.MdcKeys;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 智能体运行落库与查询（CHAT / GRAPH 共用 {@link AgentWorkflowRun}）。
 * <p>
 * 职责：start → complete / completeFailed / markCancelled；HITL resume 计时；
 * 运营侧 overview / page / detail 视图。指标经 {@link AgentRunMetrics} 上报。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunService {

    private static final String SQL_LIMIT_ONE = "LIMIT 1";
    private static final int OVERVIEW_MAX_HOURS = 24 * 30;

    private final AgentWorkflowRunMapper runMapper;
    private final AgentRunMetrics runMetrics;
    private final TrajectoryRedactor trajectoryRedactor;

    /**
     * 新建 RUNNING 记录；自动填 traceId（Micrometer MDC）。
     *
     * @param agentId              智能体主键，可为 null
     * @param agentCode            智能体编码
     * @param mode                 编排模式，不可为 null
     * @param conversationId       会话 ID
     * @param userMessage          用户原话
     * @param threadId             GRAPH checkpoint 线程 ID；CHAT 可为 null
     * @param graphVersion         图版本；CHAT 可为 null
     * @param definitionSnapshot   图定义快照；CHAT 可为 null
     * @return 已插入的运行行（含 id）
     */
    public AgentWorkflowRun start(Long agentId, String agentCode, OrchestrateModeEnum mode,
                                  String conversationId, String userMessage,
                                  String threadId, Integer graphVersion, String definitionSnapshot) {
        return start(agentId, agentCode, mode, conversationId, userMessage,
                threadId, graphVersion, definitionSnapshot, null);
    }

    /**
     * 新建 RUNNING 记录；可指定 creatorId（登录用户）。
     *
     * @param agentId              智能体主键，可为 null
     * @param agentCode            智能体编码
     * @param mode                 编排模式，不可为 null
     * @param conversationId       会话 ID
     * @param userMessage          用户原话
     * @param threadId             GRAPH checkpoint 线程 ID；CHAT 可为 null
     * @param graphVersion         图版本；CHAT 可为 null
     * @param definitionSnapshot   图定义快照；CHAT 可为 null
     * @param creatorId            发起人 AiUser.id，可为 null
     * @return 已插入的运行行（含 id）
     */
    public AgentWorkflowRun start(Long agentId, String agentCode, OrchestrateModeEnum mode,
                                  String conversationId, String userMessage,
                                  String threadId, Integer graphVersion, String definitionSnapshot,
                                  Long creatorId) {
        Objects.requireNonNull(mode, "orchestrateMode");
        AgentWorkflowRun run = newRunShell(agentId, agentCode, mode, conversationId, userMessage, creatorId);
        run.setThreadId(threadId);
        run.setGraphVersion(graphVersion);
        run.setDefinitionSnapshot(definitionSnapshot);
        run.setStatus(GraphRunStatusEnum.RUNNING.getCode());
        runMapper.insert(run);
        log.info("AgentRun start: runId={}, agentCode={}, mode={}, traceId={}, conversationId={}, threadId={}, creatorId={}",
                run.getId(), agentCode, mode, run.getTraceId(), conversationId, threadId, creatorId);
        return run;
    }

    /**
     * 配额拒绝：直接插入 FAILED 运行（errorCode=QUOTA_EXCEEDED）。
     *
     * @param agentId        智能体主键，可为 null
     * @param agentCode      智能体编码
     * @param mode           编排模式，不可为 null
     * @param conversationId 会话 ID
     * @param userMessage    用户原话
     * @param creatorId      发起人 id，可为 null
     * @param reason         拒绝原因
     * @return 已插入的 FAILED 行
     */
    public AgentWorkflowRun reject(Long agentId, String agentCode, OrchestrateModeEnum mode,
                                   String conversationId, String userMessage, Long creatorId,
                                   String reason) {
        Objects.requireNonNull(mode, "orchestrateMode");
        AgentWorkflowRun run = newRunShell(agentId, agentCode, mode, conversationId, userMessage, creatorId);
        run.setStatus(GraphRunStatusEnum.FAILED.getCode());
        run.setErrorCode(AgentRunErrorCode.QUOTA_EXCEEDED.getCode());
        run.setErrorMessage(reason != null ? reason : AgentRunErrorCode.QUOTA_EXCEEDED.getLabel());
        run.setTotalTokens(0);
        run.setDurationMs(0);
        runMapper.insert(run);
        log.warn("AgentRun reject(QUOTA): runId={}, agentCode={}, creatorId={}, reason={}",
                run.getId(), agentCode, creatorId, reason);
        return run;
    }

    /**
     * 组装运行行公共字段（agent / mode / trace / 脱敏 userMessage / creator）。
     *
     * @param agentId        智能体主键
     * @param agentCode      智能体编码
     * @param mode           编排模式
     * @param conversationId 会话 ID
     * @param userMessage    用户原话
     * @param creatorId      发起人
     * @return 未落库实体
     */
    private AgentWorkflowRun newRunShell(Long agentId, String agentCode, OrchestrateModeEnum mode,
                                         String conversationId, String userMessage, Long creatorId) {
        AgentWorkflowRun run = new AgentWorkflowRun();
        run.setAgentId(agentId);
        run.setAgentCode(agentCode);
        run.setOrchestrateMode(mode.getCode());
        run.setTraceId(MDC.get(MdcKeys.TRACE_ID));
        run.setConversationId(conversationId);
        run.setUserMessage(trajectoryRedactor.redactUserMessage(userMessage));
        run.setCreatorId(creatorId);
        return run;
    }

    /**
     * 结束运行并打点；WAITING_HUMAN 时首次写入 hitlSuspendedAt。
     *
     * @param run            运行行（须含 id）
     * @param status         终态或 WAITING_HUMAN，不可为 null
     * @param result         输出文本，可为 null
     * @param nodeExecutions 节点时间线（对象或 JSON 字符串），可为 null
     * @param stages         阶段耗时 map，可为 null
     * @param totalTokens    token 合计，null 记 0
     * @param durationMs     耗时毫秒
     * @param errorMessage   错误信息，可为 null
     * @param errorCode      错误码枚举，可为 null（FAILED 且有 message 时默认 SYSTEM）
     */
    public void complete(AgentWorkflowRun run, GraphRunStatusEnum status, String result,
                         Object nodeExecutions, Object stages, Integer totalTokens,
                         int durationMs, String errorMessage, AgentRunErrorCode errorCode) {
        if (run == null || run.getId() == null) {
            log.warn("AgentRun complete ignored: run or id is null, status={}", status);
            return;
        }
        GraphRunStatusEnum st = Objects.requireNonNull(status, "status");
        run.setStatus(st.getCode());
        String safeResult = trajectoryRedactor.redactResult(result);
        Object safeNodeExecutions = trajectoryRedactor.redactNodeExecutions(nodeExecutions);
        Object safeStages = trajectoryRedactor.redactStages(stages);
        run.setResult(safeResult);
        if (safeNodeExecutions != null) {
            try {
                run.setNodeExecutions(safeNodeExecutions instanceof String s
                        ? s : JSON.toJSONString(safeNodeExecutions));
            } catch (Exception e) {
                log.warn("serialize nodeExecutions failed: runId={}, err={}", run.getId(), e.getMessage());
            }
        }
        if (safeStages != null) {
            try {
                run.setStages(safeStages instanceof String s ? s : JSON.toJSONString(safeStages));
            } catch (Exception e) {
                log.warn("serialize stages failed: runId={}, err={}", run.getId(), e.getMessage());
            }
        }
        run.setTotalTokens(totalTokens != null ? totalTokens : 0);
        run.setDurationMs(durationMs);
        run.setErrorMessage(errorMessage);
        if (errorCode != null) {
            run.setErrorCode(errorCode.getCode());
        } else if (st == GraphRunStatusEnum.FAILED && StringUtils.hasText(errorMessage)) {
            run.setErrorCode(AgentRunErrorCode.SYSTEM.getCode());
        }
        if (st == GraphRunStatusEnum.WAITING_HUMAN && run.getHitlSuspendedAt() == null) {
            run.setHitlSuspendedAt(LocalDateTime.now());
            log.info("AgentRun HITL suspended: runId={}, threadId={}", run.getId(), run.getThreadId());
        }
        runMapper.updateById(run);

        OrchestrateModeEnum mode = OrchestrateModeEnum.ofRequired(run.getOrchestrateMode());
        runMetrics.recordRun(run.getAgentCode(), mode.name(), st.name(),
                run.getErrorCode(), durationMs, run.getTotalTokens());
        log.info("AgentRun complete: runId={}, status={}, errorCode={}, durationMs={}, tokens={}, traceId={}",
                run.getId(), st, run.getErrorCode(), durationMs, run.getTotalTokens(), run.getTraceId());
    }

    /**
     * 失败收尾（无 stages）。
     *
     * @param run        运行行
     * @param t          异常
     * @param durationMs 耗时毫秒
     */
    public void completeFailed(AgentWorkflowRun run, Throwable t, int durationMs) {
        completeFailed(run, t, durationMs, null);
    }

    /**
     * 失败收尾：按异常分类 errorCode，可选写入 stages。
     *
     * @param run        运行行
     * @param t          异常，可为 null
     * @param durationMs 耗时毫秒
     * @param stages     阶段耗时，可为 null
     */
    public void completeFailed(AgentWorkflowRun run, Throwable t, int durationMs, Object stages) {
        AgentRunErrorCode code = AgentRunErrorCode.classify(t);
        log.warn("AgentRun completeFailed: runId={}, errorCode={}, err={}, durationMs={}",
                run != null ? run.getId() : null, code,
                t != null ? t.getMessage() : null, durationMs);
        complete(run, GraphRunStatusEnum.FAILED, null, null, stages, 0, durationMs,
                t != null ? t.getMessage() : AgentRunMetricNames.UNKNOWN, code);
    }

    /**
     * HITL resume 包装：挂起计时 → 执行 resume → 回写终态；异常时落 FAILED 再抛出。
     *
     * @param threadId   checkpoint 线程 ID
     * @param agentCode  智能体编码（指标标签）
     * @param resumeCall 实际 resume 调用
     * @return 图运行结果
     * @throws Exception resume 失败时原样抛出（已落库 FAILED）
     */
    public GraphRunResponse runHitlResume(String threadId, String agentCode,
                                          Callable<GraphRunResponse> resumeCall) throws Exception {
        log.info("HITL resume begin: threadId={}, agentCode={}", threadId, agentCode);
        assertNotHitlTimedOut(threadId);
        onHitlResumed(threadId, agentCode);
        long startMs = System.currentTimeMillis();
        try {
            GraphRunResponse result = resumeCall.call();
            Objects.requireNonNull(result, "HITL resume result");
            int duration = elapsedMs(startMs);
            GraphRunStatusEnum st = GraphRunStatusEnum.ofRequired(result.getStatus());
            finishAfterHitl(threadId, st, result.getResult(), result.getNodeExecutions(),
                    result.getTotalTokens(), duration, result.getErrorMessage());
            log.info("HITL resume ok: threadId={}, status={}, durationMs={}", threadId, st, duration);
            return result;
        } catch (Exception e) {
            int duration = elapsedMs(startMs);
            log.error("HITL resume failed: threadId={}, agentCode={}, durationMs={}",
                    threadId, agentCode, duration, e);
            finishAfterHitl(threadId, GraphRunStatusEnum.FAILED, null, null, 0, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 若该 thread 最近一次运行已因 HITL 超时失败，拒绝 resume。
     *
     * @param threadId checkpoint 线程 ID
     */
    private void assertNotHitlTimedOut(String threadId) {
        if (!StringUtils.hasText(threadId)) {
            return;
        }
        AgentWorkflowRun latest = runMapper.selectOne(new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getThreadId, threadId.trim())
                .orderByDesc(AgentWorkflowRun::getId)
                .last(SQL_LIMIT_ONE));
        if (latest != null
                && GraphRunStatusEnum.FAILED.matches(latest.getStatus())
                && AgentRunErrorCode.HITL_TIMEOUT.getCode().equals(latest.getErrorCode())) {
            log.warn("HITL resume 拒绝: 已超时 threadId={}, runId={}", threadId, latest.getId());
            throw new org.deepstack.ai.kernel.exception.BusinessException(
                    org.deepstack.ai.kernel.enums.common.CommonErrorCode.BAD_REQUEST.getCode(),
                    "人工确认已超时，无法恢复: threadId=" + threadId.trim());
        }
    }

    /**
     * HITL resume：按 threadId 找到最近 WAITING_HUMAN 记录，回写等待时长并改回 RUNNING。
     *
     * @param threadId  checkpoint 线程 ID
     * @param agentCode 智能体编码（指标标签兜底）
     */
    private void onHitlResumed(String threadId, String agentCode) {
        if (!StringUtils.hasText(threadId)) {
            log.warn("HITL onHitlResumed skipped: blank threadId, agentCode={}", agentCode);
            return;
        }
        AgentWorkflowRun waiting = runMapper.selectOne(new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getThreadId, threadId.trim())
                .eq(AgentWorkflowRun::getStatus, GraphRunStatusEnum.WAITING_HUMAN.getCode())
                .orderByDesc(AgentWorkflowRun::getId)
                .last(SQL_LIMIT_ONE));
        if (waiting == null) {
            log.warn("HITL resume 未找到 WAITING_HUMAN run: threadId={}, agentCode={}", threadId, agentCode);
            return;
        }
        LocalDateTime suspended = waiting.getHitlSuspendedAt();
        long waitMs = suspended != null
                ? ChronoUnit.MILLIS.between(suspended, LocalDateTime.now())
                : 0L;
        AgentWorkflowRun patch = new AgentWorkflowRun();
        patch.setId(waiting.getId());
        patch.setHitlWaitMs((int) Math.min(waitMs, Integer.MAX_VALUE));
        patch.setStatus(GraphRunStatusEnum.RUNNING.getCode());
        runMapper.updateById(patch);
        runMetrics.recordHitlWait(
                StringUtils.hasText(agentCode) ? agentCode : waiting.getAgentCode(), waitMs);
        log.info("HITL resume 回写: runId={}, threadId={}, waitMs={}", waiting.getId(), threadId, waitMs);
    }

    /**
     * HITL resume 整图结束后更新同一 thread 最近一条运行。
     *
     * @param threadId       checkpoint 线程 ID
     * @param status         终态
     * @param result         输出文本
     * @param nodeExecutions 节点时间线
     * @param totalTokens    token 合计
     * @param durationMs     resume 段耗时
     * @param errorMessage   错误信息
     */
    private void finishAfterHitl(String threadId, GraphRunStatusEnum status, String result,
                                Object nodeExecutions, Integer totalTokens, int durationMs,
                                String errorMessage) {
        if (!StringUtils.hasText(threadId)) {
            log.warn("finishAfterHitl skipped: blank threadId, status={}", status);
            return;
        }
        AgentWorkflowRun row = runMapper.selectOne(new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getThreadId, threadId.trim())
                .orderByDesc(AgentWorkflowRun::getId)
                .last(SQL_LIMIT_ONE));
        if (row == null) {
            log.warn("finishAfterHitl 无 run: threadId={}", threadId);
            return;
        }
        AgentRunErrorCode err = status == GraphRunStatusEnum.FAILED ? AgentRunErrorCode.SYSTEM : null;
        complete(row, status, result, nodeExecutions, null, totalTokens, durationMs, errorMessage, err);
    }

    /**
     * 按主键查询运行记录。
     *
     * @param id 运行 id
     * @return 实体；id 为空或不存在返回 null
     */
    public AgentWorkflowRun getById(Long id) {
        if (id == null) {
            log.debug("AgentRun getById: id is null");
            return null;
        }
        AgentWorkflowRun run = runMapper.selectById(id);
        log.debug("AgentRun getById: id={}, found={}", id, run != null);
        return run;
    }

    /**
     * 分页查询运行记录。
     *
     * @param pageNum         页码（从 1 起）
     * @param pageSize        页大小
     * @param agentId         智能体 id 过滤，可为 null
     * @param agentCode       智能体编码过滤，可为 null
     * @param orchestrateMode 编排模式码过滤，可为 null
     * @param status          状态码过滤，可为 null
     * @param traceId         链路 id 过滤，可为 null
     * @param from            创建时间起，可为 null
     * @param to              创建时间止，可为 null
     * @return MyBatis-Plus 分页结果
     */
    public Page<AgentWorkflowRun> page(int pageNum, int pageSize, Long agentId, String agentCode,
                                       Integer orchestrateMode, Integer status, String traceId,
                                       LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<AgentWorkflowRun> q = new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(agentId != null, AgentWorkflowRun::getAgentId, agentId)
                .eq(StringUtils.hasText(agentCode), AgentWorkflowRun::getAgentCode, agentCode)
                .eq(orchestrateMode != null, AgentWorkflowRun::getOrchestrateMode, orchestrateMode)
                .eq(status != null, AgentWorkflowRun::getStatus, status)
                .eq(StringUtils.hasText(traceId), AgentWorkflowRun::getTraceId, traceId)
                .ge(from != null, AgentWorkflowRun::getCreateTime, from)
                .le(to != null, AgentWorkflowRun::getCreateTime, to)
                .orderByDesc(AgentWorkflowRun::getId);
        Page<AgentWorkflowRun> page = runMapper.selectPage(new Page<>(pageNum, pageSize), q);
        log.info("AgentRun page: pageNum={}, pageSize={}, total={}, agentCode={}, status={}, traceId={}",
                pageNum, pageSize, page.getTotal(), agentCode, status, traceId);
        return page;
    }

    /**
     * 概览聚合（默认近 hours 小时，上限 {@link #OVERVIEW_MAX_HOURS}）。
     *
     * @param hours           回溯小时数
     * @param agentId         智能体 id 过滤，可为 null
     * @param orchestrateMode 编排模式过滤，可为 null
     * @return KPI / Top / 趋势等 map
     */
    public Map<String, Object> overview(int hours, Long agentId, Integer orchestrateMode) {
        int h = Math.max(1, Math.min(hours, OVERVIEW_MAX_HOURS));
        LocalDateTime from = LocalDateTime.now().minusHours(h);
        LambdaQueryWrapper<AgentWorkflowRun> q = new LambdaQueryWrapper<AgentWorkflowRun>()
                .ge(AgentWorkflowRun::getCreateTime, from)
                .eq(agentId != null, AgentWorkflowRun::getAgentId, agentId)
                .eq(orchestrateMode != null, AgentWorkflowRun::getOrchestrateMode, orchestrateMode);
        List<AgentWorkflowRun> rows = runMapper.selectList(q);
        log.info("AgentRun overview: hours={}, rows={}, agentId={}, mode={}",
                h, rows.size(), agentId, orchestrateMode);
        return AgentRunOverviewAssembler.assemble(rows, h, from, this::toListView);
    }

    /**
     * 列表 / 概览摘要视图（API 与 overview 共用）。
     *
     * @param r 运行实体
     * @return 摘要字段 map
     */
    public Map<String, Object> toListView(AgentWorkflowRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("agentId", r.getAgentId());
        m.put("agentCode", r.getAgentCode());
        m.put("orchestrateMode", r.getOrchestrateMode());
        m.put("orchestrateModeName", OrchestrateModeEnum.labelOf(r.getOrchestrateMode()));
        m.put("status", r.getStatus());
        m.put("statusName", GraphRunStatusEnum.labelOf(r.getStatus()));
        m.put("durationMs", r.getDurationMs());
        m.put("totalTokens", r.getTotalTokens());
        m.put("errorCode", r.getErrorCode());
        m.put("errorMessage", r.getErrorMessage());
        m.put("traceId", r.getTraceId());
        m.put("conversationId", r.getConversationId());
        m.put("threadId", r.getThreadId());
        m.put("hitlWaitMs", r.getHitlWaitMs());
        m.put("createTime", r.getCreateTime());
        return m;
    }

    /**
     * 详情视图（在列表字段上追加消息、结果、节点时间线、stages 等）。
     *
     * @param r 运行实体
     * @return 详情字段 map
     */
    public Map<String, Object> toDetailView(AgentWorkflowRun r) {
        Map<String, Object> m = toListView(r);
        m.put("userMessage", r.getUserMessage());
        m.put("result", r.getResult());
        m.put("nodeExecutions", r.getNodeExecutions());
        m.put("stages", r.getStages());
        m.put("graphVersion", r.getGraphVersion());
        m.put("definitionSnapshot", r.getDefinitionSnapshot());
        m.put("hitlSuspendedAt", r.getHitlSuspendedAt());
        m.put("updateTime", r.getUpdateTime());
        return m;
    }

    /**
     * 将仍处于 RUNNING 的运行标记为 CANCELLED（幂等：仅更新 RUNNING 行）。
     *
     * @param runId  运行 id
     * @param reason 取消原因（见 {@link org.deepstack.ai.kernel.observability.AgentRunCancelReasons}）
     */
    public void markCancelled(Long runId, String reason) {
        if (runId == null) {
            log.warn("AgentRun markCancelled ignored: runId is null, reason={}", reason);
            return;
        }
        AgentWorkflowRun patch = new AgentWorkflowRun();
        patch.setId(runId);
        patch.setStatus(GraphRunStatusEnum.CANCELLED.getCode());
        patch.setErrorCode(AgentRunErrorCode.USER_CANCELLED.getCode());
        patch.setErrorMessage(reason);
        int updated = runMapper.update(patch, new LambdaUpdateWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getId, runId)
                .eq(AgentWorkflowRun::getStatus, GraphRunStatusEnum.RUNNING.getCode()));
        log.info("AgentRun cancelled: runId={}, reason={}, updatedRows={}", runId, reason, updated);
    }

    /**
     * 按 threadId 查最近一条运行（用于 HITL resume 前置校验）。
     *
     * @param threadId checkpoint 线程 ID
     * @return 最近一行；空白或不存在返回 null
     */
    public AgentWorkflowRun findLatestByThreadId(String threadId) {
        if (!StringUtils.hasText(threadId)) {
            return null;
        }
        return runMapper.selectOne(new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getThreadId, threadId.trim())
                .orderByDesc(AgentWorkflowRun::getId)
                .last(SQL_LIMIT_ONE));
    }

    /**
     * 计算自 startMs 起的耗时，并截断到 int 上限。
     *
     * @param startMs {@link System#currentTimeMillis()} 起点
     * @return 耗时毫秒
     */
    private static int elapsedMs(long startMs) {
        return (int) Math.min(System.currentTimeMillis() - startMs, Integer.MAX_VALUE);
    }
}
