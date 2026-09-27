package org.deepstack.ai.chat.service.impl;

import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum;
import org.deepstack.ai.engine.TraceConfig;
import org.deepstack.ai.runtime.GraphRuntime;
import org.deepstack.ai.agent.graph.AgentGraphSpecs;
import org.deepstack.ai.engine.GraphSpec;
import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.engine.WorkflowEventTypes;
import org.deepstack.ai.engine.support.ChatMessageKeys;
import org.deepstack.ai.kernel.observability.AgentRunCancelReasons;
import org.deepstack.ai.kernel.observability.AgentRunMetricNames;
import org.deepstack.ai.kernel.observability.AgentRunUsageKeys;
import org.deepstack.ai.kernel.observability.CardStatusFields;
import org.deepstack.ai.kernel.observability.ChatSseEvents;
import org.deepstack.ai.kernel.observability.ConversationIds;
import org.deepstack.ai.kernel.util.LoginUserIds;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.agent.observability.AgentQuotaService;
import org.deepstack.ai.agent.observability.AgentRunService;
import org.deepstack.ai.chat.support.ChatSse;
import org.deepstack.ai.chat.model.dto.internal.request.ChatRequest;
import org.deepstack.ai.chat.model.dto.internal.response.ChatResponse;
import org.deepstack.ai.chat.service.ChatConversationService;
import org.deepstack.ai.agent.service.AiAgentService;
import org.deepstack.ai.agent.service.AgentToolBindingService;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent GRAPH 编排对话器。
 * <p>
 * 由 {@link ChatServiceImpl} 在智能体 {@code orchestrateMode=GRAPH} 且配置了图定义时委托。
 * 同步 / 流式路径均经 {@link AgentRunService} 落库，SSE 事件名对齐 {@link ChatSseEvents}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentChatOrchestrator {

    private final AiAgentService aiAgentService;
    private final ChatConversationService chatConversationService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final GraphRuntime graphRuntime;
    private final AgentRunService agentRunService;
    private final AgentQuotaService agentQuotaService;
    private final AgentToolBindingService agentToolBindingService;

    /**
     * 判断请求是否走 GRAPH 编排（启用智能体 + GRAPH 模式 + 有图定义）。
     *
     * @param request 对话请求
     * @return true 表示应由本类执行
     */
    public boolean isGraphMode(ChatRequest request) {
        if (!StringUtils.hasText(request.getAgentCode())) {
            log.debug("isGraphMode: blank agentCode → false");
            return false;
        }
        AiAgent agent = aiAgentService.getByAgentCode(request.getAgentCode());
        if (agent != null && YesNo.isYes(agent.getTemplate())) {
            log.warn("isGraphMode: template agent rejected, agentCode={}", request.getAgentCode());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(), "模板不可调用");
        }
        // 正式对话走已发布图；未发布时仍可能是 GRAPH 模式（由 openSession 报错）
        boolean graph = agent != null
                && OrchestrateModeEnum.GRAPH.matches(agent.getOrchestrateMode())
                && (StringUtils.hasText(agent.getPublishedGraphDefinition())
                || StringUtils.hasText(agent.getGraphDefinition()));
        log.debug("isGraphMode: agentCode={}, graph={}", request.getAgentCode(), graph);
        return graph;
    }

    /**
     * 同步执行 GRAPH 对话；失败时落库 FAILED 并返回用户可读兜底文案。
     *
     * @param request 对话请求
     * @return 对话响应（含 status / threadId / usage）
     */
    public ChatResponse execute(ChatRequest request) {
        log.info("Agent GRAPH sync: agentCode={}, userId={}, conversationId={}",
                request.getAgentCode(), request.getUserId(), request.getConversationId());
        GraphSession session = openSession(request);

        long startMs = System.currentTimeMillis();
        GraphRunResponse result;
        try {
            result = graphRuntime.execute(session.workflow(), session.runRequest(), session.traceConfig());
        } catch (Exception e) {
            int latencyMs = (int) (System.currentTimeMillis() - startMs);
            agentRunService.completeFailed(session.runLog(), e, latencyMs);
            finishQuota(session.loginUserId(), request.getAgentCode(), 0);
            log.error("Agent GRAPH sync failed: agentCode={}, runId={}, latencyMs={}, err={}",
                    request.getAgentCode(), session.runLog().getId(), latencyMs, e.getMessage(), e);
            ChatResponse fallback = new ChatResponse(session.conversationId(),
                    "抱歉，AI 处理服务暂时不可用，请稍后重试。");
            fallback.setLatencyMs((long) latencyMs);
            return fallback;
        }

        int latencyMs = (int) (System.currentTimeMillis() - startMs);
        GraphRunStatusEnum st = GraphRunStatusEnum.ofRequired(result.getStatus());
        agentRunService.complete(session.runLog(), st, result.getResult(), result.getNodeExecutions(),
                null, result.getTotalTokens(), latencyMs, result.getErrorMessage(), null);
        finishQuota(session.loginUserId(), request.getAgentCode(),
                result.getTotalTokens() != null ? result.getTotalTokens() : 0);

        ChatResponse resp = new ChatResponse(session.conversationId(), result.getResult());
        if (st == GraphRunStatusEnum.WAITING_HUMAN && !StringUtils.hasText(resp.getContent())) {
            resp.setContent("请确认卡片后继续。");
        }
        resp.setStatus(st.getCode());
        resp.setStatusName(st.getLabel());
        resp.setThreadId(result.getThreadId());
        resp.setLatencyMs((long) latencyMs);
        resp.setTotalTokens(result.getTotalTokens() != null ? result.getTotalTokens() : 0);
        saveAgentConversationMemory(session.conversationId(), request.getMessage(), resp.getContent());
        log.info("Agent GRAPH sync done: agentCode={}, runId={}, status={}, latencyMs={}, tokens={}",
                request.getAgentCode(), session.runLog().getId(), st, latencyMs, resp.getTotalTokens());
        return resp;
    }

    /**
     * 流式执行 GRAPH：先发 conversation，再映射图事件为 SSE，末尾发 usage；
     * 错误 / 取消时更新运行落库。
     *
     * @param request 对话请求
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> executeStream(ChatRequest request) {
        log.info("Agent GRAPH stream: agentCode={}, userId={}, conversationId={}",
                request.getAgentCode(), request.getUserId(), request.getConversationId());
        GraphSession session;
        try {
            session = openSession(request);
        } catch (BusinessException e) {
            log.warn("Agent GRAPH stream openSession failed: agentCode={}, code={}, msg={}",
                    request.getAgentCode(), e.getCode(), e.getMessage());
            return Flux.error(e);
        }

        long startMs = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicBoolean runLogUpdated =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean quotaReleased =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        AgentWorkflowRun runLog = session.runLog();
        Long loginUserId = session.loginUserId();
        String agentCode = request.getAgentCode();
        String conversationId = session.conversationId();
        TraceConfig traceConfig = session.traceConfig();

        Flux<ServerSentEvent<String>> conversationEvent = Flux.just(
                ChatSse.of(ChatSseEvents.CONVERSATION, conversationId));

        return conversationEvent.concatWith(
                graphRuntime.executeStream(session.workflow(), session.runRequest(), traceConfig, runLog.getId())
                .map(event -> {
                    try {
                        String json = JSON.toJSONString(event);
                        String type = event.getEventType();
                        log.debug("GRAPH stream event: runId={}, type={}, nodeId={}",
                                runLog.getId(), type, event.getNodeId());
                        return switch (type) {
                            case WorkflowEventTypes.LLM_STREAM ->
                                    ChatSse.of(ChatSseEvents.MESSAGE, event.getResult());
                            case WorkflowEventTypes.CARD ->
                                    ChatSse.of(ChatSseEvents.CARD, event.getResult());
                            case WorkflowEventTypes.CARD_STATUS -> {
                                Map<String, Object> statusPayload = new LinkedHashMap<>();
                                statusPayload.put(CardStatusFields.PHASE, event.getPhase());
                                statusPayload.put(CardStatusFields.TOOL_NAME, event.getInput());
                                statusPayload.put(CardStatusFields.MESSAGE, event.getResult());
                                statusPayload.put(CardStatusFields.NODE_ID, event.getNodeId());
                                statusPayload.put(CardStatusFields.NODE_NAME, event.getNodeName());
                                yield ChatSse.of(ChatSseEvents.CARD_STATUS, JSON.toJSONString(statusPayload));
                            }
                            case WorkflowEventTypes.NODE_START ->
                                    ChatSse.of(ChatSseEvents.AGENT_NODE_START, json);
                            case WorkflowEventTypes.NODE_COMPLETE ->
                                    ChatSse.of(ChatSseEvents.AGENT_NODE_COMPLETE, json);
                            case WorkflowEventTypes.WORKFLOW_COMPLETE -> {
                                if (runLogUpdated.compareAndSet(false, true)) {
                                    int latencyMs = (int) (System.currentTimeMillis() - startMs);
                                    GraphRunStatusEnum st = GraphRunStatusEnum.ofRequired(event.getStatusCode());
                                    agentRunService.complete(runLog, st, event.getResult(),
                                            event.getNodeExecutions(), null, null,
                                            latencyMs, event.getErrorMessage(), null);
                                    if (quotaReleased.compareAndSet(false, true)) {
                                        finishQuota(loginUserId, agentCode, 0);
                                    }
                                    saveAgentConversationMemory(conversationId, request.getMessage(), event.getResult());
                                    log.info("GRAPH stream workflow complete: runId={}, status={}, latencyMs={}",
                                            runLog.getId(), st, latencyMs);
                                }
                                yield ChatSse.of(ChatSseEvents.WORKFLOW_COMPLETE, json);
                            }
                            default -> ChatSse.of(ChatSseEvents.AGENT_EVENT, json);
                        };
                    } catch (Exception e) {
                        log.warn("Failed to serialize workflow event: runId={}, err={}",
                                runLog.getId(), e.getMessage());
                        return ChatSse.of(ChatSseEvents.AGENT_ERROR,
                                JSON.toJSONString(Map.of(ChatSse.ERROR,
                                        e.getMessage() != null ? e.getMessage() : AgentRunMetricNames.UNKNOWN)));
                    }
                })
                .concatWith(Flux.defer(() -> {
                    Map<String, Object> usageMap = new LinkedHashMap<>();
                    usageMap.put(AgentRunUsageKeys.LATENCY_MS, System.currentTimeMillis() - startMs);
                    usageMap.put(AgentRunUsageKeys.TRACE_MODE, traceConfig.getTraceMode());
                    usageMap.put(AgentRunUsageKeys.RUN_ID, runLog.getId());
                    usageMap.put(AgentRunUsageKeys.TRACE_ID, runLog.getTraceId());
                    log.info("GRAPH stream usage: runId={}, latencyMs={}, traceId={}",
                            runLog.getId(), usageMap.get(AgentRunUsageKeys.LATENCY_MS), runLog.getTraceId());
                    return Flux.just(ChatSse.of(ChatSseEvents.USAGE, JSON.toJSONString(usageMap)));
                }))
                .doOnError(err -> {
                    if (runLogUpdated.compareAndSet(false, true)) {
                        int latencyMs = (int) (System.currentTimeMillis() - startMs);
                        if (err instanceof java.util.concurrent.CancellationException) {
                            log.info("GRAPH stream cancelled by user: runId={}", runLog.getId());
                            agentRunService.markCancelled(runLog.getId(), AgentRunCancelReasons.USER);
                        } else {
                            log.error("GRAPH stream error: runId={}, latencyMs={}, err={}",
                                    runLog.getId(), latencyMs, err.getMessage(), err);
                            agentRunService.completeFailed(runLog, err, latencyMs);
                        }
                        if (quotaReleased.compareAndSet(false, true)) {
                            finishQuota(loginUserId, agentCode, 0);
                        }
                    }
                })
                .doOnCancel(() -> {
                    if (runLogUpdated.compareAndSet(false, true)) {
                        log.info("GRAPH stream client disconnect: runId={}", runLog.getId());
                        agentRunService.markCancelled(runLog.getId(), AgentRunCancelReasons.CLIENT_DISCONNECT);
                        if (quotaReleased.compareAndSet(false, true)) {
                            finishQuota(loginUserId, agentCode, 0);
                        }
                    }
                }));
    }

    /**
     * 打开一次 GRAPH 会话：解析智能体、会话 ID、Trace、落库 RUNNING。
     *
     * @param request 对话请求
     * @return 会话上下文
     * @throws BusinessException 智能体或图未配置
     */
    private GraphSession openSession(ChatRequest request) {
        AiAgent agent = aiAgentService.getByAgentCode(request.getAgentCode());
        if (agent == null) {
            log.warn("openSession: agent not found, agentCode={}", request.getAgentCode());
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "Agent not found: " + request.getAgentCode());
        }
        if (YesNo.isYes(agent.getTemplate())) {
            log.warn("openSession: template agent rejected, agentCode={}", request.getAgentCode());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(), "模板不可调用");
        }
        if (!StringUtils.hasText(agent.getPublishedGraphDefinition())) {
            log.warn("openSession: unpublished graph, agentCode={}", request.getAgentCode());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(), "未发布图定义，请先发布");
        }

        Long loginUserId = LoginUserIds.parse(request.getUserId());
        String conversationId = resolveConversationId(agent, request.getConversationId());
        agentQuotaService.acquireOrReject(loginUserId, agent, OrchestrateModeEnum.GRAPH,
                conversationId, request.getMessage());

        chatConversationService.getOrCreate(conversationId, agent.getAgentCode(), request.getUserId());

        GraphSpec workflow = AgentGraphSpecs.fromPublished(agent);
        TraceConfig traceConfig = TraceConfig.resolve(
                agent.getTraceMode(),
                agent.getStreamProgress(),
                extractExecutionConfig(workflow)
        );

        String threadId = ConversationIds.threadIdForConversation(conversationId);
        GraphRunRequest runRequest = new GraphRunRequest();
        runRequest.setMessage(request.getMessage());
        runRequest.setUserId(request.getUserId());
        runRequest.setConversationId(conversationId);
        runRequest.setAgentCode(agent.getAgentCode());
        runRequest.setThreadId(threadId);
        runRequest.setToolCodes(agentToolBindingService.listEnabledToolCodes(agent.getId()));
        runRequest.setChatHistory(loadChatHistory(conversationId));

        AgentWorkflowRun runLog = agentRunService.start(
                agent.getId(), agent.getAgentCode(), OrchestrateModeEnum.GRAPH,
                conversationId, request.getMessage(),
                threadId, workflow.getVersion(), workflow.getDefinition(), loginUserId);

        log.info("GRAPH session opened: agentCode={}, conversationId={}, threadId={}, runId={}, traceMode={}, creatorId={}",
                agent.getAgentCode(), conversationId, threadId, runLog.getId(), traceConfig.getTraceMode(), loginUserId);
        return new GraphSession(conversationId, workflow, traceConfig, runRequest, runLog, loginUserId);
    }

    /**
     * 释放并发占位并记录日 token。
     *
     * @param loginUserId 登录用户
     * @param agentCode   智能体编码
     * @param tokens      本轮 token
     */
    private void finishQuota(Long loginUserId, String agentCode, int tokens) {
        try {
            agentQuotaService.release(loginUserId, agentCode);
            agentQuotaService.recordTokens(loginUserId, agentCode, tokens);
        } catch (Exception e) {
            log.warn("finishQuota 失败: userId={}, agentCode={}, err={}", loginUserId, agentCode, e.getMessage());
        }
    }

    /**
     * 从短期记忆加载对话历史，转为 role/content map 列表。
     *
     * @param conversationId 会话 ID
     * @return 历史条目；失败或空返回空列表
     */
    private List<Map<String, String>> loadChatHistory(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        try {
            List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);
            List<Map<String, String>> history = messages.stream()
                    .map(msg -> {
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put(ChatMessageKeys.ROLE, msg.getMessageType().getValue());
                        entry.put(ChatMessageKeys.CONTENT, msg.getText());
                        return entry;
                    })
                    .toList();
            log.debug("loadChatHistory: conversationId={}, size={}", conversationId, history.size());
            return history;
        } catch (Exception e) {
            log.warn("Failed to load chat history: conversationId={}, error={}",
                    conversationId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 将本轮 user / assistant 消息写入短期记忆。
     *
     * @param conversationId    会话 ID
     * @param userMessage       用户消息
     * @param assistantMessage  助手回复
     */
    private void saveAgentConversationMemory(String conversationId, String userMessage, String assistantMessage) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        try {
            List<Message> messages = new java.util.ArrayList<>();
            if (StringUtils.hasText(userMessage)) {
                messages.add(new UserMessage(userMessage));
            }
            if (StringUtils.hasText(assistantMessage)) {
                messages.add(new AssistantMessage(assistantMessage));
            }
            if (!messages.isEmpty()) {
                chatMemoryRepository.saveAll(conversationId, messages);
                log.debug("saveAgentConversationMemory: conversationId={}, msgs={}",
                        conversationId, messages.size());
            }
        } catch (Exception e) {
            log.warn("Failed to save agent conversation memory: conversationId={}, error={}",
                    conversationId, e.getMessage());
        }
    }

    /**
     * 从图定义 JSON 提取 executionConfig。
     *
     * @param workflow 图规格
     * @return executionConfig map；缺失或解析失败返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractExecutionConfig(GraphSpec workflow) {
        if (workflow.getDefinition() == null || workflow.getDefinition().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> definition = JSON.parseObject(
                    workflow.getDefinition(), new TypeReference<Map<String, Object>>() {});
            Object cfg = definition.get("executionConfig");
            if (cfg instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception e) {
            log.warn("Failed to extract executionConfig: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 按智能体记忆开关解析会话 ID。
     *
     * @param agent     智能体
     * @param requested 请求携带的会话 ID
     * @return 最终会话 ID
     */
    private String resolveConversationId(AiAgent agent, String requested) {
        String id = ConversationIds.resolve(YesNo.isYes(agent.getEnableMemory()), requested);
        log.debug("resolveConversationId: agentCode={}, enableMemory={}, requested={}, resolved={}",
                agent.getAgentCode(), agent.getEnableMemory(), requested, id);
        return id;
    }

    /**
     * 单次 GRAPH 调用的会话上下文。
     *
     * @param conversationId 会话 ID
     * @param workflow       图规格
     * @param traceConfig    轨迹配置
     * @param runRequest     图运行请求
     * @param runLog         落库运行行
     * @param loginUserId    登录用户 AiUser.id，可为 null
     */
    private record GraphSession(
            String conversationId,
            GraphSpec workflow,
            TraceConfig traceConfig,
            GraphRunRequest runRequest,
            AgentWorkflowRun runLog,
            Long loginUserId
    ) {
    }
}
