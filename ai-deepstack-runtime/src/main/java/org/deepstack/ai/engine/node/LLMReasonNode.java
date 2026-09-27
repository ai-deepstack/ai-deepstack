package org.deepstack.ai.engine.node;


import org.deepstack.ai.runtime.AgentNodeContext;
import org.deepstack.ai.runtime.RunContext;
import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.engine.WorkflowStreamEvent;
import org.deepstack.ai.engine.support.ChatMessageKeys;
import org.deepstack.ai.engine.support.MessageTextExtractor;
import org.deepstack.ai.engine.support.NodeRetryExecutor;
import org.deepstack.ai.kernel.tool.ToolContextKeys;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSession;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class LLMReasonNode implements AsyncNodeAction<WorkflowState> {

    private final AgentNodeContext context;
    private final String systemPrompt;
    private final Double temperature;
    private final String modelCode;
    private final String outputVar;
    private final List<String> inputVars;
    private final int maxRetries;
    private final String nodeId;
    private final String nodeName;

    /**
     * 构造 LLM 推理节点（无画布节点 id/name）。
     *
     * @param context    运行时上下文
     * @param properties 节点属性：systemPrompt / temperature / modelCode / outputVar 等
     */
    @SuppressWarnings("unchecked")
    public LLMReasonNode(AgentNodeContext context, Map<String, Object> properties) {
        this(context, properties, null, null);
    }

    /**
     * 构造 LLM 推理节点。
     *
     * @param context    运行时上下文
     * @param properties 节点属性
     * @param nodeId     画布节点 ID（日志/进度用）
     * @param nodeName   画布节点显示名
     */
    @SuppressWarnings("unchecked")
    public LLMReasonNode(AgentNodeContext context, Map<String, Object> properties,
                         String nodeId, String nodeName) {
        this.context = context;
        this.systemPrompt = (String) properties.getOrDefault("systemPrompt", "你是一个AI助手");
        this.temperature = properties.get("temperature") != null
                ? ((Number) properties.get("temperature")).doubleValue() : 0.7;
        Object mc = properties.get("modelCode");
        this.modelCode = mc != null ? mc.toString() : null;
        this.outputVar = (String) properties.getOrDefault("outputVar", "response");
        this.inputVars = (List<String>) properties.getOrDefault("inputVars", List.of());
        this.maxRetries = properties.get("maxRetries") != null
                ? ((Number) properties.get("maxRetries")).intValue() : NodeRetryExecutor.DEFAULT_MAX_ATTEMPTS;
        this.nodeId = nodeId;
        this.nodeName = nodeName;
    }

    /**
     * 执行 LLM 推理节点：组装上下文、调用模型（可带工具），写回 {@code outputVar}。
     *
     * @param state 工作流状态
     * @return 包含输出变量的状态增量
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        log.info("LLMReasonNode.apply: nodeId={}, nodeName={}, modelCode={}, outputVar={}",
                nodeId, nodeName, modelCode, outputVar);
        if (modelCode == null || modelCode.isBlank()) {
            log.warn("LLMReasonNode.apply 失败: 未配置 modelCode, nodeId={}", nodeId);
            return CompletableFuture.completedFuture(Map.of(outputVar, "错误：未选择LLM模型(modelCode)"));
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (String varName : inputVars) {
            Object val = state.value(varName).orElse("");
            if (val != null && !val.toString().isEmpty()) {
                contextBuilder.append("[").append(varName).append("]\n").append(val).append("\n\n");
            }
        }

        Object messagesObj = state.value("messages").orElse(List.of());
        String userMessage = MessageTextExtractor.lastMessageText(messagesObj);

        // 若有对话历史则拼成段落
        String chatHistorySection = buildChatHistorySection(state);

        try {
            String fullUserMessage = chatHistorySection + contextBuilder.toString() + userMessage;

            // 优先从 state 解析 userId / conversationId，否则回退 ThreadLocal。
            // State 优先：LangGraph 可能在其他线程执行节点，ThreadLocal 会丢失。
            String userId = state.value(WorkflowState.USER_ID).map(Object::toString).orElse("");
            if (userId == null || userId.isEmpty()) {
                userId = context.currentUserId() != null ? context.currentUserId() : "";
            }
            String conversationId = state.value(WorkflowState.CONVERSATION_ID)
                    .map(Object::toString).orElse("");
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = context.currentConversationId() != null
                        ? context.currentConversationId() : "";
            }

            // 是否有可用的卡片推送器
            java.util.function.Consumer<Object> cardEmitter = context.getCardEmitter();

            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(context.getApiModelName(modelCode))
                    .temperature(temperature);

            // 允许集来自编排注入的 toolCodes；经 ToolDisclosureSession 决定本轮暴露集
            List<String> allowCodes = new ArrayList<>();
            Object toolCodesObj = state.value(WorkflowState.TOOL_CODES).orElse(null);
            if (toolCodesObj instanceof List<?> rawCodes) {
                for (Object o : rawCodes) {
                    if (o != null && !o.toString().isBlank()) {
                        allowCodes.add(o.toString());
                    }
                }
            }
            ToolDisclosureSession disclosureSession = null;
            var factory = context.getToolDisclosureSessionFactory();
            if (factory != null && !allowCodes.isEmpty()) {
                disclosureSession = factory.open(allowCodes);
                RunContext rc = RunContext.current();
                if (rc != null) {
                    rc.setToolDisclosureSession(disclosureSession);
                }
            }
            List<ToolCallback> toolCallbacks = disclosureSession != null
                    ? new ArrayList<>(disclosureSession.exposedToolCallbacks())
                    : (allowCodes.isEmpty()
                        ? new ArrayList<>()
                        : new ArrayList<>(context.getToolPort().resolveToolCallbacksByCodes(allowCodes)));
            log.debug("LLMReasonNode: exposed {} tool callbacks (allow={})",
                    toolCallbacks.size(), allowCodes.size());

            // 按请求构建 ToolContext，向 @Tool 方法注入 userId/conversationId/cardEmitter
            // （键常量集中在 ToolContextKeys）
            Map<String, Object> ctxMap = new HashMap<>();
            ctxMap.put(ToolContextKeys.USER_ID, userId);
            ctxMap.put(ToolContextKeys.CONVERSATION_ID, conversationId);
            ctxMap.put(ToolContextKeys.MODEL_CODE, modelCode);
            if (cardEmitter != null) {
                ctxMap.put(ToolContextKeys.CARD_EMITTER, cardEmitter);
                ctxMap.put("cardPort", context.getCardPort());
            }
            ToolContext toolContext = new ToolContext(ctxMap);

            // 直接使用 ChatModel + 手工工具循环，避免 ToolCallingAdvisor 在
            // LangGraph4j 异步线程上流式时的 reactor 问题（非 HTTP 线程上流式模式下
            // DelegatingToolCallbackResolver 会抛 "toolName cannot be null or empty"）。
            ChatModel chatModel = context.getChatModel(modelCode);

            // 构建 messages 列表
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage(fullUserMessage));

            // 流式 sink：实时将 LLM chunk 推到 SSE 流。
            Sinks.Many<WorkflowStreamEvent> sink = context.getStreamSink();

            // driveStream：仅当存在 sink 且为最外层（非工具调用）LLM 调用时为 true。
            // 中间工具决策轮（通常只有 tool_call JSON，无面向用户的文本）不推流；
            // 最终文本响应轮才推流。
            boolean driveStream = sink != null && nodeId != null;

            NodeRetryExecutor retryExecutor = NodeRetryExecutor.builder()
                    .maxAttempts(maxRetries)
                    .nodeDescription(nodeName != null ? "LLMReasonNode:" + nodeName : "LLMReasonNode")
                    .build();

            final ToolDisclosureSession sessionForLoop = disclosureSession;
            LLMStreamResult streamResult;
            try {
                streamResult = retryExecutor.execute(() -> callWithToolLoopStreaming(
                        messages, optionsBuilder, toolCallbacks, sessionForLoop, chatModel, toolContext,
                        sink, nodeId, nodeName, driveStream));
            } catch (Exception e) {
                log.error("LLMReasonNode tool loop failed: {}", e.getMessage(), e);
                return CompletableFuture.failedFuture(e);
            }

            String text = streamResult.accumulatedText();
            log.info("LLMReasonNode: outputVar={}, len={}, temp={}, toolRounds={}, streamed={}",
                    outputVar, text.length(), temperature, streamResult.toolRounds(), driveStream);

            return CompletableFuture.completedFuture(Map.of(outputVar, text));
        } catch (Exception e) {
            log.error("LLMReasonNode failed after {} attempts: {}", maxRetries, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 使用 ChatModel 手工驱动工具循环的流式版本，绕过 ChatClient/ToolCallingAdvisor。
     * <p>
     * Spring AI 2.0.0 的 ToolCallingAdvisor 在异步线程上流式模式有已知问题
     *（DelegatingToolCallbackResolver 抛出 "toolName cannot be null or empty"）。
     * 通过 {@code spring.ai.chat.client.tool-calling.enabled=false} 关闭自动工具调用，
     * 并在此手工驱动工具循环。
     * </p>
     *
     * <p>Spring AI 2.0.0 的 {@code DefaultToolCallingManager.executeToolCalls(Prompt, ChatResponse)}
     * 不接受 {@link ToolContext}，因此 {@code @Tool} 无法经 manager 拿到按请求状态
     *（userId、conversationId、cardEmitter）。改为直接调用 {@link ToolCallback#call(String, ToolContext)}，
     * 并自行组装 {@code ToolResponseMessage}。</p>
     *
     * <p>每轮使用 {@link ChatModel#stream(Prompt)}。<b>流式协议：</b>
     * 文字边收边推 SSE（{@code LLM_STREAM}）；出现 tool call 后本轮停止推文字；
     * 卡片仍由工具侧经 {@code CARD} 整包下发。轮结束后合并工具调用——有工具则执行；
     * 无工具则结束（最终文字轮已实时推送，不必再整段回放）。</p>
     *
     * <p><b>流式到阻塞边界：</b>Spring AI 的 {@code ChatModel.stream()} 返回
     * {@code Flux&lt;ChatResponse&gt;}。工具调用仍须排空本轮流才能合并参数；
     * 用 {@code blockLast()} 同步排空，但文字 chunk 在过程中已推给 sink。</p>
     *
     * @param messages       system + user 消息
     * @param optionsBuilder OpenAI 选项（model、temperature）
     * @param toolCallbacks  已注册的工具回调（智能体无工具时为空列表）
     * @param chatModel      该 modelId 对应的 ChatModel 实例
     * @param toolContext    按请求上下文（传入 {@link ToolCallback#call(String, ToolContext)}）
     * @param sink           推送 LLM chunk 的 SSE sink；禁用流式时为 null
     * @param nodeId         SSE 事件打标用的节点 ID；禁用流式时为 null
     * @param nodeName       SSE 事件打标用的节点显示名
     * @param driveStream    true 则边收边推文字；false 仅缓冲（同步路径）
     * @return {@link LLMStreamResult}，包含最终累积文本与工具轮次数
     */
    private LLMStreamResult callWithToolLoopStreaming(
            List<Message> messages,
            OpenAiChatOptions.Builder optionsBuilder,
            List<ToolCallback> toolCallbacks,
            ToolDisclosureSession disclosureSession,
            ChatModel chatModel,
            ToolContext toolContext,
            Sinks.Many<WorkflowStreamEvent> sink,
            String nodeId,
            String nodeName,
            boolean driveStream) {

        List<Message> conversationHistory = new ArrayList<>(messages);
        StringBuilder accumulatedText = new StringBuilder();
        int toolRounds = 0;

        // [PERF] 整体计时：从进入循环到最终返回
        long methodStartNanos = System.nanoTime();
        // [PERF] 首字延迟：从方法进入到首个文字 chunk 推到 sink
        java.util.concurrent.atomic.AtomicLong firstChunkRef = new java.util.concurrent.atomic.AtomicLong(-1);
        // [PERF] LLM RTT 累计（所有轮次的 stream() 阻塞时长）
        long llmStreamTotalNanos = 0;
        // [PERF] 工具执行累计（所有轮次的 cb.call() 时长）
        long toolExecTotalNanos = 0;
        // [PERF] merge 累计（所有轮次的 ToolCallMerger 合并时长）
        long mergeTotalNanos = 0;

        while (true) {
            // 渐进披露：每轮刷新 exposed（load_tools 后下一轮可见业务工具）
            if (disclosureSession != null) {
                toolCallbacks = new ArrayList<>(disclosureSession.exposedToolCallbacks());
            }
            var options = OpenAiChatOptions.builder()
                    .model(optionsBuilder.build().getModel())
                    .temperature(optionsBuilder.build().getTemperature())
                    .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                    .build();
            Prompt prompt = new Prompt(conversationHistory, options);

            // 诊断：dump 会话历史（DEBUG 级别，避免 INFO 刷屏）
            if (log.isDebugEnabled()) {
                log.debug("LLMReasonNode: round={} conversationHistory:", toolRounds);
                for (int i = 0; i < conversationHistory.size(); i++) {
                    Message m = conversationHistory.get(i);
                    String type = m.getMessageType() != null ? m.getMessageType().name() : "?";
                    String text = m.getText();
                    StringBuilder toolInfo = new StringBuilder();
                    if (m instanceof AssistantMessage am) {
                        if (am.hasToolCalls()) {
                            toolInfo.append(" toolCalls=[");
                            for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                                toolInfo.append("{name=").append(tc.name())
                                        .append(",id=").append(tc.id())
                                        .append(",type=").append(tc.type())
                                        .append(",argsLen=").append(tc.arguments() != null ? tc.arguments().length() : -1)
                                        .append("},");
                            }
                            toolInfo.append("]");
                        }
                    } else if (m instanceof ToolResponseMessage trm) {
                        toolInfo.append(" toolResponses=[");
                        for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                            toolInfo.append("{id=").append(tr.id())
                                    .append(",name=").append(tr.name())
                                    .append(",respLen=").append(tr.responseData() != null ? tr.responseData().length() : -1)
                                    .append("},");
                        }
                        toolInfo.append("]");
                    }
                    log.debug("  [{}] type={}, textLen={}, textPreview={}{}",
                            i, type, text != null ? text.length() : -1,
                            text != null ? (text.length() > 80 ? text.substring(0, 80) + "..." : text) : "(null)",
                            toolInfo);
                }
            }

            // === 用 stream() 输出每一轮：文字边收边推；出现 tool call 后本轮停推文字 ===
            List<ChatResponse> roundResponses = new ArrayList<>();
            StringBuilder roundText = new StringBuilder();
            java.util.concurrent.atomic.AtomicBoolean sawToolCall = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicBoolean liveEmitted = new java.util.concurrent.atomic.AtomicBoolean(false);

            // [PERF] 本轮 LLM stream 计时（用原子变量，doOnNext lambda 闭包需要 final）
            long roundStreamStart = System.nanoTime();
            java.util.concurrent.atomic.AtomicLong roundFirstChunkRef = new java.util.concurrent.atomic.AtomicLong(-1);
            java.util.concurrent.atomic.AtomicInteger roundChunkCountRef = new java.util.concurrent.atomic.AtomicInteger(0);
            try {
                chatModel.stream(prompt)
                        .doOnNext(cr -> {
                            // [PERF] 记录本轮首 chunk 时间
                            roundFirstChunkRef.compareAndSet(-1, System.nanoTime());
                            roundChunkCountRef.incrementAndGet();
                            roundResponses.add(cr);
                            if (chunkHasToolCalls(cr)) {
                                sawToolCall.set(true);
                            }
                            String chunk = extractText(cr);
                            if (chunk.isEmpty()) {
                                return;
                            }
                            roundText.append(chunk);
                            // 工具决策轮可能夹杂半截文本：一旦看到 tool call 即停推，避免泄漏
                            if (driveStream && sink != null && nodeId != null && !sawToolCall.get()) {
                                liveEmitted.set(true);
                                firstChunkRef.compareAndSet(-1, System.nanoTime());
                                try {
                                    sink.tryEmitNext(WorkflowStreamEvent.llmStream(nodeId, nodeName, chunk));
                                } catch (Exception e) {
                                    log.debug("Failed to emit LLM stream chunk: {}", e.getMessage());
                                }
                            }
                        })
                        .blockLast();
            } catch (Exception e) {
                log.error("LLMReasonNode: chatModel.stream() failed on round {}: {}",
                        toolRounds, e.getClass().getName(), e);
                dumpConversationHistory(conversationHistory);
                throw e;
            }
            long roundStreamEnd = System.nanoTime();
            long roundStreamDuration = roundStreamEnd - roundStreamStart;
            llmStreamTotalNanos += roundStreamDuration;
            long roundFirstChunkVal = roundFirstChunkRef.get();
            int roundChunkCnt = roundChunkCountRef.get();
            log.info("[PERF] round={} LLM stream: {}ms (chunks={}, firstChunkLatency={}ms, driveStream={}, roundTextLen={})",
                    toolRounds, roundStreamDuration / 1_000_000, roundChunkCnt,
                    roundFirstChunkVal > 0 ? (roundFirstChunkVal - roundStreamStart) / 1_000_000 : -1,
                    driveStream, roundText.length());

            if (roundResponses.isEmpty()) {
                log.info("[PERF] round={} stream returned empty, breaking", toolRounds);
                break;
            }

            // === 从所有 stream chunk 中合并工具调用数据 ===
            long mergeStart = System.nanoTime();
            ToolCallMerger merger = new ToolCallMerger();
            for (ChatResponse cr : roundResponses) {
                if (cr.getResult() == null || cr.getResult().getOutput() == null) continue;
                if (!cr.getResult().getOutput().hasToolCalls()) continue;
                for (AssistantMessage.ToolCall tc : cr.getResult().getOutput().getToolCalls()) {
                    merger.merge(tc);
                }
            }
            long mergeDuration = System.nanoTime() - mergeStart;
            mergeTotalNanos += mergeDuration;

            if (merger.hasToolCalls()) {
                // === 工具调用轮：文字已按「遇 tool call 即停推」处理；此处只执行工具（卡片整包走 CARD）===
                toolRounds++;
                log.info("[PERF] round={} tool-call round (merge: {}ms, mergedCount={}, liveEmitted={}, roundTextLen={})",
                        toolRounds - 1, mergeDuration / 1_000_000, merger.getMergedCount(),
                        liveEmitted.get(), roundText.length());

                List<ToolCallMerger.MergedToolCall> mergedTools = merger.getMergedToolCalls();
                boolean roundHasCardTool = mergedTools.stream().anyMatch(mt -> isCardTool(mt.name));
                List<AssistantMessage.ToolCall> assistantToolCalls = new ArrayList<>(mergedTools.size());
                for (ToolCallMerger.MergedToolCall mt : mergedTools) {
                    String resolvedName = mt.name;
                    if ((resolvedName == null || resolvedName.isEmpty()) && toolCallbacks.size() == 1) {
                        resolvedName = toolCallbacks.get(0).getToolDefinition().name();
                        log.info("LLMReasonNode: inferred tool name from single registered tool: {}", resolvedName);
                    }
                    String resolvedId = mt.id;
                    if (resolvedId == null || resolvedId.isEmpty()) {
                        resolvedId = UUID.randomUUID().toString();
                        log.debug("LLMReasonNode: generated synthetic tool call id: {}", resolvedId);
                    }
                    String resolvedType = mt.type != null ? mt.type : "function";
                    mt.name = resolvedName;
                    mt.id = resolvedId;
                    mt.type = resolvedType;
                    assistantToolCalls.add(new AssistantMessage.ToolCall(
                            resolvedId, resolvedType, resolvedName, mt.arguments.toString()));
                }

                // 用合并后的全部 toolCalls 构建 AssistantMessage
                AssistantMessage assistantMessage = AssistantMessage.builder()
                        .content("")
                        .toolCalls(assistantToolCalls)
                        .build();
                conversationHistory.add(assistantMessage);

                // 执行全部工具
                // [PERF] 工具执行计时
                long toolStart = System.nanoTime();
                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>(mergedTools.size());
                boolean anyCardSuccess = false;
                StringBuilder cardFinalizeText = new StringBuilder();

                for (ToolCallMerger.MergedToolCall mt : mergedTools) {
                    final String resolvedName = mt.name;
                    final String resolvedId = mt.id;
                    final String resolvedArgs = mt.arguments.toString();

                    ToolCallback cb = toolCallbacks.stream()
                            .filter(c -> c.getToolDefinition().name().equals(resolvedName))
                            .findFirst().orElse(null);
                    boolean cardTool = isCardTool(resolvedName);
                    if (roundHasCardTool && !cardTool) {
                        log.info("LLMReasonNode: skip non-card tool in confirm round, name={}", resolvedName);
                        toolResponses.add(new ToolResponseMessage.ToolResponse(
                                resolvedId, resolvedName,
                                "skipped: a confirmation card was proposed; wait for the user before calling this tool"));
                        continue;
                    }
                    if (cardTool) {
                        // 卡片工具执行前推状态，前端可做等待动画；真正卡片仍走 CARD 事件
                        emitCardStatus(sink, nodeId, nodeName, "generating", resolvedName,
                                "正在生成结构化卡片，请稍候…");
                    }
                    if (cb == null) {
                        log.warn("LLMReasonNode: tool callback not found for name={}", resolvedName);
                        toolResponses.add(new ToolResponseMessage.ToolResponse(
                                resolvedId, resolvedName, "tool not found: " + resolvedName));
                        if (cardTool) {
                            emitCardStatus(sink, nodeId, nodeName, "failed", resolvedName,
                                    "卡片工具未找到：" + resolvedName);
                        }
                        continue;
                    }
                    try {
                        String toolResult = cb.call(resolvedArgs, toolContext);
                        toolResponses.add(new ToolResponseMessage.ToolResponse(
                                resolvedId, resolvedName, toolResult));
                        // 任一卡片工具成功：标记短接，跳过收尾 LLM 轮
                        if (cardTool && isSuccessfulCardToolResult(toolResult)) {
                            anyCardSuccess = true;
                            if (toolResult != null && !toolResult.isBlank()) {
                                if (cardFinalizeText.length() > 0) {
                                    cardFinalizeText.append('\n');
                                }
                                cardFinalizeText.append(toolResult.trim());
                            }
                        }
                    } catch (Exception toolEx) {
                        log.error("LLMReasonNode: tool execution failed, name={}: {}",
                                resolvedName, toolEx.getMessage(), toolEx);
                        toolResponses.add(new ToolResponseMessage.ToolResponse(
                                resolvedId, resolvedName, "tool failed: " + toolEx.getMessage()));
                        if (cardTool) {
                            emitCardStatus(sink, nodeId, nodeName, "failed", resolvedName,
                                    "卡片生成失败：" + toolEx.getMessage());
                        }
                    }
                }

                long toolDuration = System.nanoTime() - toolStart;
                toolExecTotalNanos += toolDuration;
                log.info("[PERF] round={} tool execution: {}ms (toolCount={})",
                        toolRounds - 1, toolDuration / 1_000_000, mergedTools.size());

                conversationHistory.add(ToolResponseMessage.builder()
                        .responses(toolResponses)
                        .build());

                if (anyCardSuccess) {
                    String finalizeText = cardFinalizeText.toString();
                    accumulatedText.append(finalizeText);
                    if (driveStream && sink != null && nodeId != null && !finalizeText.isEmpty()) {
                        firstChunkRef.compareAndSet(-1, System.nanoTime());
                        try {
                            sink.tryEmitNext(WorkflowStreamEvent.llmStream(
                                    nodeId, nodeName, finalizeText));
                        } catch (Exception e) {
                            log.debug("Failed to emit card finalize text: {}", e.getMessage());
                        }
                    }
                    log.info("LLMReasonNode: card tool succeeded, skip final LLM round (tools={})",
                            mergedTools.size());
                    break;
                }
                // 循环继续下一轮
            } else {
                // === 最终纯文本轮：文字应已 chunk 级推送；未推过时兜底整段推一次（异常路径）===
                if (driveStream && sink != null && nodeId != null
                        && !liveEmitted.get() && roundText.length() > 0) {
                    firstChunkRef.compareAndSet(-1, System.nanoTime());
                    try {
                        sink.tryEmitNext(WorkflowStreamEvent.llmStream(
                                nodeId, nodeName, roundText.toString()));
                    } catch (Exception e) {
                        log.debug("Failed to emit LLM result fallback: {}", e.getMessage());
                    }
                }

                accumulatedText.append(roundText);
                log.info("[PERF] round={} final text round (liveEmitted={}), breaking",
                        toolRounds, liveEmitted.get());
                break;
            }
        }

        long methodEndNanos = System.nanoTime();
        long totalDurationMs = (methodEndNanos - methodStartNanos) / 1_000_000;
        long firstChunkVal = firstChunkRef.get();
        long firstChunkMs = firstChunkVal > 0 ? (firstChunkVal - methodStartNanos) / 1_000_000 : -1;
        log.info("[PERF] === callWithToolLoopStreaming TOTAL === {}ms (toolRounds={}, LLM stream={}ms, tool exec={}ms, merge={}ms, firstChunkLatency={}ms)",
                totalDurationMs, toolRounds,
                llmStreamTotalNanos / 1_000_000,
                toolExecTotalNanos / 1_000_000,
                mergeTotalNanos / 1_000_000,
                firstChunkMs);

        return new LLMStreamResult(accumulatedText.toString(), toolRounds);
    }

    /**
     * 是否为会产出结构化卡片的 tool（命名约定：propose* / *card*）。
     */
    private static boolean isCardTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String n = toolName.toLowerCase(Locale.ROOT);
        return n.contains("propose") || n.contains("card");
    }

    /**
     * 卡片 tool 是否执行成功（失败串仍走后续 LLM 兜底说明）。
     */
    private static boolean isSuccessfulCardToolResult(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return false;
        }
        String t = toolResult.toLowerCase(Locale.ROOT);
        return !t.startsWith("tool failed")
                && !t.contains("失败")
                && !t.contains("tool not found");
    }

    /**
     * 向 SSE sink 推送卡片生命周期状态（generating / failed）。成功后前端以 CARD 事件取消等待。
     */
    private void emitCardStatus(Sinks.Many<WorkflowStreamEvent> sink,
                                String nodeId, String nodeName,
                                String phase, String toolName, String message) {
        if (sink == null) {
            return;
        }
        try {
            sink.tryEmitNext(WorkflowStreamEvent.cardStatus(
                    nodeId, nodeName, phase, toolName, message));
        } catch (Exception e) {
            log.debug("Failed to emit card status: {}", e.getMessage());
        }
    }

    /**
     * 从流式 {@link ChatResponse} chunk 中提取文本内容。
     */
    private String extractText(ChatResponse cr) {
        if (cr == null || cr.getResult() == null || cr.getResult().getOutput() == null) {
            return "";
        }
        String t = cr.getResult().getOutput().getText();
        return t != null ? t : "";
    }

    /**
     * 判断流式 chunk 是否携带 tool call（可能与文本同包）。
     */
    private static boolean chunkHasToolCalls(ChatResponse cr) {
        if (cr == null || cr.getResult() == null || cr.getResult().getOutput() == null) {
            return false;
        }
        return cr.getResult().getOutput().hasToolCalls();
    }

    /**
     * 流式工具循环结果载体：最终累积文本与已执行的工具调用轮次数。
     */
    private record LLMStreamResult(String accumulatedText, int toolRounds) {
    }

    /**
     * 从工作流 state 构建格式化的对话历史段落。
     * 无可用对话历史时返回空串。
     */
    @SuppressWarnings("unchecked")
    private String buildChatHistorySection(WorkflowState state) {
        Object chatHistoryObj = state.value(WorkflowState.CHAT_HISTORY).orElse(null);
        if (chatHistoryObj == null) {
            return "";
        }
        if (!(chatHistoryObj instanceof List<?> historyList) || historyList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[对话历史]\n");
        for (Object item : historyList) {
            if (item instanceof Map<?, ?> entry) {
                Object roleObj = entry.get(ChatMessageKeys.ROLE);
                Object contentObj = entry.get(ChatMessageKeys.CONTENT);
                String role = roleObj != null ? roleObj.toString() : "";
                String content = contentObj != null ? contentObj.toString() : "";
                if (!role.isEmpty() && !content.isEmpty()) {
                    sb.append(role).append(": ").append(content).append("\n");
                }
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 以 ERROR 级别 dump 会话历史，供诊断使用。
     * 在 LLM 调用因意外错误失败时由 catch 块调用。
     */
    private void dumpConversationHistory(List<Message> conversationHistory) {
        log.error("LLMReasonNode: conversationHistory ({} messages):",
                conversationHistory.size());
        for (int i = 0; i < conversationHistory.size(); i++) {
            Message m = conversationHistory.get(i);
            log.error("  [{}] type={} textLen={} text={}",
                    i,
                    m.getMessageType() != null ? m.getMessageType().name() : "?",
                    m.getText() != null ? m.getText().length() : -1,
                    m.getText() != null ? (m.getText().length() > 200
                            ? m.getText().substring(0, 200) + "..." : m.getText()) : "(null)");
            if (m instanceof AssistantMessage am && am.hasToolCalls()) {
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    log.error("    toolCall: name={}, id={}, type={}, argsLen={}, args={}",
                            tc.name(), tc.id(), tc.type(),
                            tc.arguments() != null ? tc.arguments().length() : -1,
                            tc.arguments() != null ? (tc.arguments().length() > 500
                                    ? tc.arguments().substring(0, 500) + "..." : tc.arguments()) : "(null)");
                }
            } else if (m instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    log.error("    toolResponse: id={}, name={}, respLen={}, resp={}",
                            tr.id(), tr.name(),
                            tr.responseData() != null ? tr.responseData().length() : -1,
                            tr.responseData() != null ? (tr.responseData().length() > 200
                                    ? tr.responseData().substring(0, 200) + "..." : tr.responseData()) : "(null)");
                }
            }
        }
    }

    /**
     * 从 stream 的 ToolCall delta 中合并 name/id/arguments，支持并行多工具调用。
     * <p>
     * stream 中工具调用的分片特征：
     * <ol>
     *   <li>首 chunk：name="tool_name", id="call_xxx", arguments=""（或首段）</li>
     *   <li>后续 chunk：name 空、id 空、arguments="partial fragment..."</li>
     * </ol>
     * 合并策略：
     * <ul>
     *   <li>有 tool call id → 按 id 归入已有条目</li>
     *   <li>无 id 但有非空 name → 开启新条目</li>
     *   <li>均空（参数碎片）→ 追加到当前匹配条目</li>
     * </ul>
     * <p>
     * 注意：部分兼容 API 可能不在 stream delta 中发送 name/id，
     * 此时 name/id 在所有 chunk 中均为空，需要在外部通过单工具推断或 fallback 处理。
     */
    private static class ToolCallMerger {
        private final LinkedHashMap<String, MergedToolCall> entries = new LinkedHashMap<>();
        private MergedToolCall current;
        private int anonSeq;

        void merge(AssistantMessage.ToolCall tc) {
            if (tc == null) {
                return;
            }
            String id = tc.id();
            String name = tc.name();
            boolean hasId = id != null && !id.isEmpty();
            boolean hasName = name != null && !name.isEmpty();

            MergedToolCall target;
            if (hasId) {
                target = entries.computeIfAbsent(id, k -> new MergedToolCall());
                current = target;
            } else if (hasName) {
                // 无 id 时，新非空 name 开启新条目
                String key = "anon#" + (anonSeq++);
                target = new MergedToolCall();
                entries.put(key, target);
                current = target;
            } else if (current != null) {
                target = current;
            } else if (!entries.isEmpty()) {
                // 无 current 时追加到最后一条
                target = null;
                for (MergedToolCall e : entries.values()) {
                    target = e;
                }
                current = target;
            } else {
                String key = "anon#" + (anonSeq++);
                target = new MergedToolCall();
                entries.put(key, target);
                current = target;
            }

            if (target == null) {
                return;
            }
            if (hasName && (target.name == null || target.name.isEmpty())) {
                target.name = name;
            }
            if (hasId && (target.id == null || target.id.isEmpty())) {
                target.id = id;
            }
            if (tc.type() != null && !tc.type().isEmpty()
                    && (target.type == null || target.type.isEmpty())) {
                target.type = tc.type();
            }
            if (tc.arguments() != null) {
                target.arguments.append(tc.arguments());
            }
        }

        boolean hasToolCalls() {
            return !entries.isEmpty();
        }

        /** 已合并的工具调用条数（并行多工具时 &gt; 1）。 */
        int getMergedCount() {
            return entries.size();
        }

        List<MergedToolCall> getMergedToolCalls() {
            return new ArrayList<>(entries.values());
        }

        static final class MergedToolCall {
            String name;
            String id;
            String type;
            final StringBuilder arguments = new StringBuilder();
        }
    }
}
