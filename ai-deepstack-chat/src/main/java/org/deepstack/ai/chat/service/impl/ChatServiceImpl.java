package org.deepstack.ai.chat.service.impl;


import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum;
import org.deepstack.ai.infra.llm.AiChatClientFactory;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.agent.observability.AgentQuotaService;
import org.deepstack.ai.agent.observability.AgentRunService;
import org.deepstack.ai.chat.model.dto.internal.request.ChatRequest;
import org.deepstack.ai.chat.model.dto.internal.response.ChatResponse;
import org.deepstack.ai.chat.service.ChatConversationService;
import org.deepstack.ai.chat.service.ChatService;
import org.deepstack.ai.chat.support.ChatSse;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.agent.service.AiAgentService;
import org.deepstack.ai.agent.service.AgentToolBindingService;
import org.deepstack.ai.knowledge.model.dto.internal.KnowledgeRetrieveResult;
import org.deepstack.ai.knowledge.service.KnowledgeRetrieveFacade;
import org.deepstack.ai.agent.service.userprompt.UserPromptService;
import org.deepstack.ai.kernel.observability.AgentRunCancelReasons;
import org.deepstack.ai.kernel.observability.AgentRunUsageKeys;
import org.deepstack.ai.kernel.observability.ChatSseEvents;
import org.deepstack.ai.kernel.observability.ConversationIds;
import org.deepstack.ai.kernel.util.LoginUserIds;
import org.deepstack.ai.kernel.observability.AgentRunStageKeys;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.memory.LongTermMemoryStore;
import org.deepstack.ai.memory.MemoryItem;
import org.deepstack.ai.memory.MemoryQuery;
import org.deepstack.ai.memory.MemoryWrite;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSession;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSessionFactory;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 对话服务 - DB 驱动通道实现
 * <p>
 * 代码仅是通道，所有对话行为由数据库配置驱动：
 * <ul>
 *   <li>模型供应商/base-url/api-key 由 ai_model 表配置，通过 {@link AiChatClientFactory} 按 modelCode 动态构建</li>
 *   <li>L1 系统提示词/温度/max_tokens/top_p/记忆窗口/响应格式由 ai_agent 表配置</li>
 *   <li>L2 用户全域提示词通过 {@link UserPromptService} 加载</li>
 *   <li>L3 RAG 知识检索：按智能体绑定的知识库，经 KnowledgeRetrieveFacade 多库并行召回后注入 system prompt</li>
 *   <li>L4 会话记忆通过 ChatMemory advisor + conversationId 隔离</li>
 *   <li>L5 长期记忆：enable_long_term_memory 时经 {@link LongTermMemoryStore} 召回注入</li>
 * </ul>
 * 每次请求按 scene.modelCode 取对应的 OpenAiChatModel，构造临时 ChatClient 调用。
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ChatServiceImpl implements ChatService {

    /** 拼 prompt 总超时默认（毫秒）；可被 sys_config chat.context.timeout-ms 覆盖 */
    private static final int DEFAULT_CONTEXT_TIMEOUT_MS = 10_000;

    private final AiChatClientFactory aiChatClientFactory;
    private final AiModelService aiModelService;
    private final AiAgentService aiAgentService;
    private final ChatConversationService chatConversationService;
    private final ChatMemory chatMemory;
    private final UserPromptService userPromptService;
    private final KnowledgeRetrieveFacade knowledgeRetrieveFacade;
    private final ToolDisclosureSessionFactory toolDisclosureSessionFactory;
    private final AgentToolBindingService agentToolBindingService;
    private final AgentChatOrchestrator agentChatOrchestrator;
    private final AgentRunService agentRunService;
    private final AgentQuotaService agentQuotaService;
    private final ObjectProvider<LongTermMemoryStore> longTermMemoryStore;
    private final SysConfigPort sysConfigPort;
    /**
     * 画像 / RAG / 记忆并行拼装专用池。
     * <p>禁止用 knowledgeMultiKbExecutor：RAG 内会再往多库编排池提交，同池嵌套会死锁。</p>
     */
    @Qualifier("chatContextExecutor")
    private final Executor chatContextExecutor;

    /**
     * 同步对话：按智能体配置驱动模型 / 工具 / RAG / 记忆。
     * <p>GRAPH 模式委托 {@link AgentChatOrchestrator}；否则走本类 ChatClient 直连路径。</p>
     *
     * @param request 对话请求（须含 agentCode、message）
     * @return 对话响应（含 usage / cards）
     * @throws BusinessException 参数非法，或智能体/模型不存在、已禁用
     */
    @Override
    public ChatResponse chat(ChatRequest request) {
        log.info("chat: agentCode={}, conversationId={}, userId={}",
                request.getAgentCode(), request.getConversationId(), request.getUserId());
        // GRAPH 编排：委托给 AgentChatOrchestrator
        if (agentChatOrchestrator.isGraphMode(request)) {
            log.info("chat 走 GRAPH 编排: agentCode={}", request.getAgentCode());
            return agentChatOrchestrator.execute(request);
        }

        ChatContext ctx = prepare(request);
        Long loginUserId = LoginUserIds.parse(request.getUserId());
        agentQuotaService.acquireOrReject(loginUserId, ctx.scene, OrchestrateModeEnum.CHAT,
                ctx.conversationId, request.getMessage());

        long startMs = System.currentTimeMillis();
        AgentWorkflowRun runLog = agentRunService.start(
                ctx.scene.getId(), ctx.scene.getAgentCode(), OrchestrateModeEnum.CHAT,
                ctx.conversationId, request.getMessage(), null, null, null, loginUserId);

        log.info("Chat invoke (sync): agentCode={}, modelCode={}, provider={}, apiModel={}, baseUrl={}, conversationId={}, runId={}",
                ctx.scene.getAgentCode(), ctx.scene.getModelCode(),
                ctx.modelConfig.getProvider(), ctx.modelConfig.getApiModelName(),
                ctx.modelConfig.getBaseUrl(), ctx.conversationId, runLog.getId());

        java.util.concurrent.ConcurrentLinkedQueue<org.deepstack.ai.card.model.dto.response.ChatCard> cardQueue =
                new java.util.concurrent.ConcurrentLinkedQueue<>();

        org.springframework.ai.chat.model.ChatResponse aiResponse;
        long llmStart = System.currentTimeMillis();
        try {
            aiResponse = buildRequestSpec(ctx, request, cardQueue::add)
                    .call()
                    .chatResponse();
        } catch (Exception e) {
            int latencyMs = (int) (System.currentTimeMillis() - startMs);
            ctx.stages.put(AgentRunStageKeys.LLM_MS, System.currentTimeMillis() - llmStart);
            agentRunService.completeFailed(runLog, e, latencyMs, ctx.stages);
            finishQuota(loginUserId, ctx.scene.getAgentCode(), 0);
            log.error("Chat sync error: agentCode={}, conversationId={}, errorClass={}, errorMsg={}",
                    ctx.scene.getAgentCode(), ctx.conversationId,
                    e.getClass().getName(), e.getMessage(), e);
            throw e;
        }

        ctx.stages.put(AgentRunStageKeys.LLM_MS, System.currentTimeMillis() - llmStart);
        int latencyMs = (int) (System.currentTimeMillis() - startMs);
        String content = aiResponse.getResult() != null && aiResponse.getResult().getOutput() != null
                ? aiResponse.getResult().getOutput().getText()
                : null;

        ChatResponse resp = new ChatResponse(ctx.conversationId, content);
        resp.setLatencyMs((long) latencyMs);
        fillUsage(resp, aiResponse.getMetadata());

        if (!cardQueue.isEmpty()) {
            resp.setCards(new java.util.ArrayList<>(cardQueue));
        }

        agentRunService.complete(runLog, GraphRunStatusEnum.SUCCESS, content, null, ctx.stages,
                resp.getTotalTokens(), latencyMs, null, null);
        finishQuota(loginUserId, ctx.scene.getAgentCode(), resp.getTotalTokens());

        log.info("Chat response (sync): agentCode={}, conversationId={}, contentLength={}, latencyMs={}, promptTokens={}, completionTokens={}, totalTokens={}, cards={}, runId={}",
                ctx.scene.getAgentCode(), ctx.conversationId,
                content != null ? content.length() : 0, latencyMs,
                resp.getPromptTokens(), resp.getCompletionTokens(), resp.getTotalTokens(),
                resp.getCards() != null ? resp.getCards().size() : 0, runLog.getId());

        maybeRememberUserMessage(ctx.scene, request);
        return resp;
    }

    /**
     * SSE 流式对话：按智能体配置驱动模型 / 工具 / RAG / 记忆。
     * <p>GRAPH 模式委托 {@link AgentChatOrchestrator}；否则走本类 ChatClient 流式路径，
     * 结束后追加 card / usage 事件。</p>
     *
     * @param request 对话请求（须含 agentCode、message）
     * @return SSE 事件流（默认 data 为文本 chunk；另有 event=card / usage）
     * @throws BusinessException 参数非法，或智能体/模型不存在、已禁用（在订阅前 prepare 阶段抛出）
     */
    @Override
    public Flux<ServerSentEvent<String>> streamChat(ChatRequest request) {
        log.info("streamChat: agentCode={}, conversationId={}, userId={}",
                request.getAgentCode(), request.getConversationId(), request.getUserId());
        // GRAPH 编排：委托给 AgentChatOrchestrator
        if (agentChatOrchestrator.isGraphMode(request)) {
            log.info("streamChat 走 GRAPH 编排: agentCode={}", request.getAgentCode());
            return agentChatOrchestrator.executeStream(request);
        }

        ChatContext ctx = prepare(request);
        Long loginUserId = LoginUserIds.parse(request.getUserId());
        try {
            agentQuotaService.acquireOrReject(loginUserId, ctx.scene, OrchestrateModeEnum.CHAT,
                    ctx.conversationId, request.getMessage());
        } catch (BusinessException e) {
            return Flux.error(e);
        }

        long startMs = System.currentTimeMillis();
        AgentWorkflowRun runLog = agentRunService.start(
                ctx.scene.getId(), ctx.scene.getAgentCode(), OrchestrateModeEnum.CHAT,
                ctx.conversationId, request.getMessage(), null, null, null, loginUserId);

        log.info("Chat invoke (stream): agentCode={}, modelCode={}, provider={}, apiModel={}, baseUrl={}, conversationId={}, runId={}",
                ctx.scene.getAgentCode(), ctx.scene.getModelCode(),
                ctx.modelConfig.getProvider(), ctx.modelConfig.getApiModelName(),
                ctx.modelConfig.getBaseUrl(), ctx.conversationId, runLog.getId());

        AtomicLong chunkCount = new AtomicLong(0);
        AtomicLong firstTokenMs = new AtomicLong(0);
        AtomicReference<ChatResponseMetadata> lastMetadata = new AtomicReference<>();
        AtomicReference<StringBuilder> contentBuf = new AtomicReference<>(new StringBuilder());
        java.util.concurrent.atomic.AtomicBoolean runDone = new java.util.concurrent.atomic.AtomicBoolean(false);
        long llmStart = System.currentTimeMillis();

        java.util.concurrent.ConcurrentLinkedQueue<org.deepstack.ai.card.model.dto.response.ChatCard> cardQueue =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        java.util.function.Consumer<org.deepstack.ai.card.model.dto.response.ChatCard> cardEmitter = cardQueue::add;

        ChatClient.ChatClientRequestSpec requestSpec = buildRequestSpec(ctx, request, cardEmitter);

        return requestSpec
                .stream()
                .chatResponse()
                .map(aiResp -> {
                    if (aiResp.getMetadata() != null) {
                        lastMetadata.set(aiResp.getMetadata());
                    }

                    long n = chunkCount.incrementAndGet();
                    if (n == 1) {
                        firstTokenMs.set(System.currentTimeMillis());
                        log.info("Chat stream first chunk received: conversationId={}", ctx.conversationId);
                    }

                    String text = "";
                    if (aiResp.getResult() != null && aiResp.getResult().getOutput() != null) {
                        text = aiResp.getResult().getOutput().getText();
                        if (text == null) text = "";
                    }
                    if (!text.isEmpty()) {
                        contentBuf.get().append(text);
                    }
                    return ChatSse.data(text);
                })
                .concatWith(Flux.defer(() -> {
                    java.util.List<ServerSentEvent<String>> tail = new java.util.ArrayList<>();

                    org.deepstack.ai.card.model.dto.response.ChatCard card;
                    while ((card = cardQueue.poll()) != null) {
                        try {
                            String cardJson = JSON.toJSONString(card);
                            tail.add(ChatSse.of(ChatSseEvents.CARD, cardJson));
                        } catch (Exception e) {
                            log.warn("Failed to serialize card: {}", e.getMessage());
                        }
                    }

                    long totalChunks = chunkCount.get();
                    int totalLatencyMs = (int) (System.currentTimeMillis() - startMs);
                    Long firstTokenLatencyMs = firstTokenMs.get() > 0 ? firstTokenMs.get() - startMs : null;
                    ctx.stages.put(AgentRunStageKeys.LLM_MS, System.currentTimeMillis() - llmStart);
                    if (firstTokenLatencyMs != null) {
                        ctx.stages.put(AgentRunStageKeys.FIRST_TOKEN_MS, firstTokenLatencyMs);
                    }

                    Map<String, Object> usageMap = new LinkedHashMap<>();
                    usageMap.put(AgentRunUsageKeys.LATENCY_MS, totalLatencyMs);
                    usageMap.put(AgentRunUsageKeys.FIRST_TOKEN_LATENCY_MS, firstTokenLatencyMs);
                    usageMap.put(AgentRunUsageKeys.RUN_ID, runLog.getId());
                    usageMap.put(AgentRunUsageKeys.TRACE_ID, runLog.getTraceId());

                    Integer totalTokens = null;
                    ChatResponseMetadata meta = lastMetadata.get();
                    if (meta != null && meta.getUsage() != null) {
                        Usage usage = meta.getUsage();
                        usageMap.put(AgentRunUsageKeys.PROMPT_TOKENS, usage.getPromptTokens());
                        usageMap.put(AgentRunUsageKeys.COMPLETION_TOKENS, usage.getCompletionTokens());
                        usageMap.put(AgentRunUsageKeys.TOTAL_TOKENS, usage.getTotalTokens());
                        totalTokens = usage.getTotalTokens();
                    }

                    if (runDone.compareAndSet(false, true)) {
                        agentRunService.complete(runLog, GraphRunStatusEnum.SUCCESS,
                                contentBuf.get().toString(), null, ctx.stages,
                                totalTokens, totalLatencyMs, null, null);
                        finishQuota(loginUserId, ctx.scene.getAgentCode(),
                                totalTokens != null ? totalTokens : 0);
                    }

                    if (totalChunks == 0) {
                        log.error("Chat stream completed with ZERO chunks (LLM returned empty stream). " +
                                        "conversationId={}, agentCode={}, modelCode={}, provider={}, apiModel={}, baseUrl={}.",
                                ctx.conversationId, ctx.scene.getAgentCode(), ctx.scene.getModelCode(),
                                ctx.modelConfig.getProvider(), ctx.modelConfig.getApiModelName(),
                                ctx.modelConfig.getBaseUrl());
                    } else {
                        log.info("Chat stream completed: conversationId={}, totalChunks={}, latencyMs={}, firstTokenLatencyMs={}, usage={}, cards={}, runId={}",
                                ctx.conversationId, totalChunks, totalLatencyMs, firstTokenLatencyMs, usageMap, tail.size(),
                                runLog.getId());
                    }

                    String usageJson;
                    try {
                        usageJson = JSON.toJSONString(usageMap);
                    } catch (Exception e) {
                        usageJson = "{}";
                    }
                    tail.add(ChatSse.of(ChatSseEvents.USAGE, usageJson));

                    return Flux.fromIterable(tail);
                }))
                .doOnSubscribe(sub -> log.info("Chat stream subscribed: conversationId={}", ctx.conversationId))
                .doOnError(err -> {
                    log.error("Chat stream error: agentCode={}, conversationId={}, errorClass={}, errorMsg={}",
                            ctx.scene.getAgentCode(), ctx.conversationId,
                            err.getClass().getName(), err.getMessage(), err);
                    if (runDone.compareAndSet(false, true)) {
                        ctx.stages.put(AgentRunStageKeys.LLM_MS, System.currentTimeMillis() - llmStart);
                        agentRunService.completeFailed(runLog, err,
                                (int) (System.currentTimeMillis() - startMs), ctx.stages);
                        finishQuota(loginUserId, ctx.scene.getAgentCode(), 0);
                    }
                })
                .doOnCancel(() -> {
                    log.warn("Chat stream cancelled: conversationId={}", ctx.conversationId);
                    if (runDone.compareAndSet(false, true)) {
                        agentRunService.markCancelled(runLog.getId(), AgentRunCancelReasons.CLIENT_DISCONNECT);
                        finishQuota(loginUserId, ctx.scene.getAgentCode(), 0);
                    }
                });
    }

    // ============================================================
    // 内部：准备 + 构建
    // ============================================================

    /**
     * 校验请求、加载智能体/模型，解析 conversationId 并组装 ChatContext。
     *
     * @param request 对话请求（须含 agentCode、message）
     * @return 已就绪的对话上下文（智能体、模型、会话 ID、系统提示词等）
     * @throws BusinessException agentCode/message 为空，或智能体/模型不存在、已禁用
     */
    private ChatContext prepare(ChatRequest request) {
        log.info("prepare: agentCode={}, conversationId={}, userId={}",
                request.getAgentCode(), request.getConversationId(), request.getUserId());
        if (!StringUtils.hasText(request.getAgentCode())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "agentCode 不能为空");
        }
        if (!StringUtils.hasText(request.getMessage())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "message 不能为空");
        }

        AiAgent scene = aiAgentService.getByAgentCode(request.getAgentCode());
        if (scene == null) {
            log.warn("prepare 失败: 智能体不存在或已禁用 agentCode={}", request.getAgentCode());
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "智能体不存在或已禁用: " + request.getAgentCode());
        }
        if (YesNo.isYes(scene.getTemplate())) {
            log.warn("prepare 失败: 模板不可调用 agentCode={}", request.getAgentCode());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(), "模板不可调用");
        }
        if (!StringUtils.hasText(scene.getModelCode())) {
            log.warn("prepare 失败: 智能体未绑定模型 agentCode={}", request.getAgentCode());
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "智能体未绑定模型: " + request.getAgentCode());
        }

        AiModel modelConfig = aiModelService.getByModelCode(scene.getModelCode());
        if (modelConfig == null) {
            log.warn("prepare 失败: 模型不存在或已禁用 modelCode={}", scene.getModelCode());
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "模型不存在或已禁用: modelCode=" + scene.getModelCode());
        }
        OpenAiChatModel chatModel = aiChatClientFactory.getChatModel(modelConfig.getModelCode());

        String conversationId = resolveConversationId(scene, request.getConversationId());
        // 持久化会话记录，供 reject 等跨请求场景反查 agentCode / modelCode
        chatConversationService.getOrCreate(conversationId, scene.getAgentCode(), request.getUserId());
        Map<String, Object> stages = new java.util.concurrent.ConcurrentHashMap<>();
        String systemPrompt = buildSystemPrompt(scene, request.getUserId(), request, stages);
        OpenAiChatOptions.Builder optionsBuilder = buildChatOptions(modelConfig, scene);

        return new ChatContext(scene, modelConfig, chatModel, conversationId, systemPrompt, optionsBuilder, request.getMessage(), stages);
    }

    /**
     * 构建 ChatClient 请求规格：记忆 advisor、系统/用户消息、可选工具与 toolContext。
     *
     * @param ctx         已准备的对话上下文
     * @param request     原始对话请求（取 userId 等写入 toolContext）
     * @param cardEmitter 工具产出卡片时的回调（同步入队 / 流式尾部 emit）
     * @return 可直接 call/stream 的 ChatClient 请求规格
     */
    private ChatClient.ChatClientRequestSpec buildRequestSpec(ChatContext ctx,
                                                              ChatRequest request,
                                                              java.util.function.Consumer<org.deepstack.ai.card.model.dto.response.ChatCard> cardEmitter) {
        // 编排：绑定允许集 → DisclosureSession → 本轮暴露的 ToolCallback
        List<String> allowCodes = agentToolBindingService.listEnabledToolCodes(ctx.scene.getId());
        ToolDisclosureSession session = toolDisclosureSessionFactory.open(allowCodes);
        List<org.springframework.ai.tool.ToolCallback> toolCallbacks =
                new ArrayList<>(session.exposedToolCallbacks());
        log.info("buildRequestSpec: agentCode={}, conversationId={}, allowTools={}, exposedTools={}, metaTools={}",
                ctx.scene.getAgentCode(), ctx.conversationId, allowCodes.size(),
                toolCallbacks.size(), session.metaToolCallbacks().size());
        java.util.Map<String, Object> toolCtxMap = new java.util.HashMap<>();
        toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.USER_ID, request.getUserId());
        toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.CONVERSATION_ID, ctx.conversationId);
        toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.MODEL_CODE, ctx.modelConfig.getModelCode());
        toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.CARD_EMITTER, cardEmitter);

        String systemPrompt = ctx.systemPrompt;
        // 存在元工具（tool_search / load_tools）时，追加工具发现指引
        if (!session.metaToolCallbacks().isEmpty()) {
            systemPrompt = systemPrompt + "\n\n[工具使用] 请先用 tool_search 查找可用工具，再用 load_tools 加载后再调用业务工具。";
        }

        ChatClient.ChatClientRequestSpec spec = ChatClient.builder(ctx.chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build()
                .prompt()
                .system(systemPrompt)
                .user(ctx.userMessage)
                .options(ctx.optionsBuilder)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, ctx.conversationId));

        // 仅在有暴露工具时挂 .tools()，避免空工具列表破坏流式语义
        if (!toolCallbacks.isEmpty()) {
            spec = spec.tools(toolCallbacks).toolContext(toolCtxMap);
        }
        return spec;
    }

    /**
     * 解析会话 ID：关闭记忆时生成一次性 ID，否则沿用请求或新建 UUID。
     *
     * @param scene     智能体配置（读 enableMemory）
     * @param requested 请求侧传入的 conversationId，可为 null
     * @return 实际使用的会话 ID（无记忆时带 {@link ConversationIds#NO_MEMORY_PREFIX} 前缀）
     */
    private String resolveConversationId(AiAgent scene, String requested) {
        return ConversationIds.resolve(YesNo.isYes(scene.getEnableMemory()), requested);
    }

    /**
     * 组装多层系统提示词：L1 智能体 + L2 用户画像 + L3 RAG + L5 长期记忆 + 输出格式约束。
     * <p>画像 / RAG / 记忆在 {@code chatContextExecutor} 并行获取，再按固定顺序写入；
     * 总等待受 {@code chat.context.timeout-ms} 约束；各阶段耗时写入 {@code stages}（O7）。</p>
     */
    private String buildSystemPrompt(AiAgent scene, String userId, ChatRequest request, Map<String, Object> stages) {
        StringBuilder sb = new StringBuilder(scene.getSystemPrompt());
        long t0 = System.currentTimeMillis();
        int timeoutMs = Math.max(1_000, sysConfigPort.getInt(
                SysConfigKeys.CHAT_CONTEXT_TIMEOUT_MS, DEFAULT_CONTEXT_TIMEOUT_MS));
        log.info("buildSystemPrompt 并行启动: agentCode={}, userId={}, timeoutMs={}",
                scene.getAgentCode(), userId, timeoutMs);

        CompletableFuture<String> userPromptF = CompletableFuture.supplyAsync(() -> {
            long s = System.currentTimeMillis();
            try {
                if (!StringUtils.hasText(userId)) {
                    return null;
                }
                return userPromptService.getUserPrompt(userId);
            } catch (Exception e) {
                log.warn("加载用户画像失败: userId={}, err={}", userId, e.getMessage());
                return null;
            } finally {
                stages.put(AgentRunStageKeys.USER_PROMPT_MS, System.currentTimeMillis() - s);
            }
        }, chatContextExecutor);

        CompletableFuture<String> ragF = CompletableFuture.supplyAsync(() -> {
            long s = System.currentTimeMillis();
            try {
                return retrieveKnowledgeContext(scene, request);
            } catch (Exception e) {
                log.warn("RAG 并行失败: agentCode={}, err={}", scene.getAgentCode(), e.getMessage());
                return null;
            } finally {
                stages.put(AgentRunStageKeys.RAG_MS, System.currentTimeMillis() - s);
            }
        }, chatContextExecutor);

        CompletableFuture<String> memoryF = CompletableFuture.supplyAsync(() -> {
            long s = System.currentTimeMillis();
            try {
                if (!YesNo.isYes(scene.getEnableLongTermMemory()) || !StringUtils.hasText(userId)) {
                    return null;
                }
                LongTermMemoryStore ltm = longTermMemoryStore.getIfAvailable();
                if (ltm == null) {
                    return null;
                }
                boolean graph = YesNo.isYes(scene.getEnableGraphMemory());
                List<MemoryItem> items = ltm.recall(new MemoryQuery(
                        userId,
                        scene.getAgentCode(),
                        request != null ? request.getMessage() : "",
                        8,
                        graph
                ));
                return ltm.formatContext(items);
            } catch (Exception e) {
                log.warn("长期记忆并行失败: userId={}, err={}", userId, e.getMessage());
                return null;
            } finally {
                stages.put(AgentRunStageKeys.MEMORY_MS, System.currentTimeMillis() - s);
            }
        }, chatContextExecutor);

        try {
            CompletableFuture.allOf(userPromptF, ragF, memoryF)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("buildSystemPrompt 总超时: agentCode={}, timeoutMs={}, costMs={}",
                    scene.getAgentCode(), timeoutMs, System.currentTimeMillis() - t0);
            userPromptF.cancel(true);
            ragF.cancel(true);
            memoryF.cancel(true);
            stages.put(AgentRunStageKeys.PROMPT_TIMEOUT, true);
        } catch (Exception e) {
            log.warn("buildSystemPrompt allOf 异常: agentCode={}, err={}",
                    scene.getAgentCode(), e.getMessage());
            userPromptF.cancel(true);
            ragF.cancel(true);
            memoryF.cancel(true);
        }

        String userPrompt = joinOrNull(userPromptF, "userPrompt");
        if (StringUtils.hasText(userPrompt)) {
            sb.append("\n\n[用户画像]\n").append(userPrompt);
            stages.put(AgentRunStageKeys.HAS_USER_PROMPT, true);
        }

        String ragContext = joinOrNull(ragF, "rag");
        if (StringUtils.hasText(ragContext)) {
            sb.append("\n\n[知识库参考]\n").append(ragContext);
            sb.append("\n\n请优先基于以上知识库参考内容回答用户问题。如果参考内容不足以回答，请明确说明。");
            stages.put(AgentRunStageKeys.HAS_RAG, true);
        }

        String memCtx = joinOrNull(memoryF, "memory");
        if (StringUtils.hasText(memCtx)) {
            sb.append("\n\n[长期记忆]\n").append(memCtx);
            stages.put(AgentRunStageKeys.HAS_MEMORY, true);
        }

        Integer format = scene.getResponseFormat();
        if (format != null && format == org.deepstack.ai.kernel.enums.model.ResponseFormatEnum.JSON_SCHEMA.getCode()
                && StringUtils.hasText(scene.getResponseSchema())) {
            sb.append("\n\n[输出格式]\n请严格按以下 JSON Schema 输出，不要输出任何额外文字：\n")
                    .append(scene.getResponseSchema());
        } else if (format != null && format == org.deepstack.ai.kernel.enums.model.ResponseFormatEnum.JSON.getCode()) {
            sb.append("\n\n[输出格式]\n请以合法的 JSON 对象输出，不要输出任何额外文字。");
        }

        long promptMs = System.currentTimeMillis() - t0;
        stages.put(AgentRunStageKeys.PROMPT_MS, promptMs);
        log.info("buildSystemPrompt 完成: agentCode={}, costMs={}, hasUser={}, hasRag={}, hasMem={}, promptLen={}, stages={}",
                scene.getAgentCode(), promptMs,
                StringUtils.hasText(userPrompt), StringUtils.hasText(ragContext),
                StringUtils.hasText(memCtx), sb.length(), stages);
        return sb.toString();
    }

    /**
     * 安全取并行结果：已取消 / 未完成 / 异常时返回 null，避免二次 join 阻塞。
     */
    private static String joinOrNull(CompletableFuture<String> future, String label) {
        if (future == null || future.isCancelled()) {
            log.debug("buildSystemPrompt {} 已取消或空", label);
            return null;
        }
        if (!future.isDone()) {
            future.cancel(true);
            log.warn("buildSystemPrompt {} 超时后仍未完成，已取消", label);
            return null;
        }
        try {
            return future.getNow(null);
        } catch (Exception e) {
            log.warn("buildSystemPrompt {} 取结果失败: {}", label, e.getMessage());
            return null;
        }
    }

    /**
     * CHAT 模式：启用长期记忆时，将本轮用户话写入记忆（图谱由 enable_graph_memory 控制）。
     * <p>写入失败仅打日志，不影响主对话返回。</p>
     *
     * @param scene   智能体配置
     * @param request 本轮对话请求（取 userId、message、conversationId）
     */
    private void maybeRememberUserMessage(AiAgent scene, ChatRequest request) {
        if (!YesNo.isYes(scene.getEnableLongTermMemory())) {
            return;
        }
        LongTermMemoryStore ltm = longTermMemoryStore.getIfAvailable();
        if (ltm == null || request == null || !StringUtils.hasText(request.getUserId())
                || !StringUtils.hasText(request.getMessage())) {
            return;
        }
        try {
            boolean graph = YesNo.isYes(scene.getEnableGraphMemory());
            ltm.remember(new MemoryWrite(
                    request.getUserId(),
                    scene.getAgentCode(),
                    request.getMessage(),
                    "episode",
                    graph,
                    List.of(),
                    request.getConversationId(),
                    null
            ));
        } catch (Exception e) {
            log.warn("LTM remember failed: {}", e.getMessage());
        }
    }

    /**
     * RAG 知识检索：按智能体绑定的知识库召回。
     * <p>
     * 经 {@link KnowledgeRetrieveFacade#retrieveAll} 多库并行（Semaphore 限流），
     * 按绑定顺序拼接；单库超时/失败不影响其他库。
     * </p>
     *
     * @param scene   智能体配置
     * @param request 对话请求
     * @return 拼接后的知识上下文文本，无结果返回 null
     */
    private String retrieveKnowledgeContext(AiAgent scene, ChatRequest request) {
        List<String> kbCodes = aiAgentService.getKnowledgeBaseCodes(scene.getId());
        if (kbCodes == null || kbCodes.isEmpty()) {
            log.debug("RAG 无知识库绑定: agentCode={}", scene.getAgentCode());
            return null;
        }
        log.info("RAG 多库并行召回: agentCode={}, kbCount={}, codes={}",
                scene.getAgentCode(), kbCodes.size(), kbCodes);
        try {
            List<KnowledgeRetrieveResult> parts = knowledgeRetrieveFacade.retrieveAll(
                    kbCodes, request.getMessage(), 5, null);
            String ctx = knowledgeRetrieveFacade.formatPromptContext(parts);
            log.info("RAG 完成: agentCode={}, sections={}, contextLen={}",
                    scene.getAgentCode(),
                    parts != null ? parts.stream().filter(p -> p != null && p.getTexts() != null
                            && !p.getTexts().isEmpty()).count() : 0,
                    ctx != null ? ctx.length() : 0);
            return ctx;
        } catch (Exception e) {
            // 召回整体异常不阻断对话
            log.error("RAG retrieveAll 失败: agentCode={}, err={}",
                    scene.getAgentCode(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建模型调用参数：apiModelName 来自 ai_model，温度/max_tokens/top_p/响应格式来自 ai_agent。
     *
     * @param modelConfig 启用中的模型配置
     * @param scene       智能体配置（参数覆盖）
     * @return OpenAiChatOptions 构建器（由调用方挂到 request）
     */
    private OpenAiChatOptions.Builder buildChatOptions(AiModel modelConfig, AiAgent scene) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(modelConfig.getApiModelName());

        if (scene.getTemperature() != null) {
            builder.temperature(scene.getTemperature().doubleValue());
        }
        if (scene.getMaxTokens() != null) {
            builder.maxTokens(scene.getMaxTokens());
        }
        if (scene.getTopP() != null) {
            builder.topP(scene.getTopP().doubleValue());
        }

        Integer format = scene.getResponseFormat();
        // JSON / JSON_SCHEMA：强制上游以 JSON 对象格式返回
        if (format != null && (format == org.deepstack.ai.kernel.enums.model.ResponseFormatEnum.JSON.getCode()
                || format == org.deepstack.ai.kernel.enums.model.ResponseFormatEnum.JSON_SCHEMA.getCode())) {
            builder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT).build());
        }

        return builder;
    }

    /**
     * 将 Spring AI Usage 元数据填入 ChatResponse（prompt/completion/total tokens）。
     *
     * @param resp 待填充的对话响应
     * @param meta 模型响应元数据；为 null 或无 usage 时直接返回
     */
    private void fillUsage(ChatResponse resp, ChatResponseMetadata meta) {
        if (meta == null || meta.getUsage() == null) return;
        Usage usage = meta.getUsage();
        resp.setPromptTokens(usage.getPromptTokens());
        resp.setCompletionTokens(usage.getCompletionTokens());
        resp.setTotalTokens(usage.getTotalTokens());
    }

    /**
     * 释放并发占位并记录日 token。
     *
     * @param loginUserId 登录用户
     * @param agentCode   智能体编码
     * @param tokens      本轮 token
     */
    private void finishQuota(Long loginUserId, String agentCode, Integer tokens) {
        try {
            agentQuotaService.release(loginUserId, agentCode);
            agentQuotaService.recordTokens(loginUserId, agentCode, tokens != null ? tokens : 0);
        } catch (Exception e) {
            log.warn("finishQuota 失败: userId={}, agentCode={}, err={}", loginUserId, agentCode, e.getMessage());
        }
    }

    /**
     * 单次对话请求的内部上下文（智能体、模型、会话、提示词与用户消息）。
     *
     * @param scene          智能体配置
     * @param modelConfig    模型配置
     * @param chatModel      按 modelCode 构建的 ChatModel
     * @param conversationId 解析后的会话 ID
     * @param systemPrompt   组装后的系统提示词
     * @param optionsBuilder 模型调用参数构建器
     * @param userMessage    本轮用户消息
     * @param stages         阶段耗时（O7），随 run 落库
     */
    private record ChatContext(
            AiAgent scene,
            AiModel modelConfig,
            OpenAiChatModel chatModel,
            String conversationId,
            String systemPrompt,
            OpenAiChatOptions.Builder optionsBuilder,
            String userMessage,
            Map<String, Object> stages
    ) {
    }
}
