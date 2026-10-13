package org.deepstack.ai.card.service.impl;

import org.deepstack.ai.card.model.dto.response.ChatCard;
import org.deepstack.ai.card.model.entity.ChatCardEntity;
import org.deepstack.ai.card.spi.AgentModelResolver;
import org.deepstack.ai.card.spi.ConversationAgentResolver;
import org.deepstack.ai.infra.llm.AiChatClientFactory;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.runtime.spi.ToolPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 被拒绝卡片的 LLM 重生成服务。
 * <p>
 * reject 落地后若无需 HITL 续跑，则由本服务异步驱动 LLM + 卡片工具重新产出一张新卡片：
 * 解析模型、组装提示词、调用工具、按约定通过 {@code cardEmitter} 把新卡片推回 SSE 流，
 * 失败自动重试一次，仍失败则经 sink 推送错误文案。
 * </p>
 * <p>
 * 与 {@link ChatCardServiceImpl} 的分工：外层服务负责 reject 的状态落地与 SSE 流骨架的搭建；
 * 本服务只负责“用 LLM 重新生成一张卡片”这段与模型/工具相关的副作用，不感知鉴权与状态流转。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCardRegenerationService {

    private final ToolPort toolPort;
    private final AiChatClientFactory aiChatClientFactory;
    private final AgentModelResolver agentModelResolver;
    private final ConversationAgentResolver conversationSceneResolver;

    /**
     * 异步通过 LLM + card tool 重新产出卡片。
     * <p>
     * LLM 成功 → tool 内部 {@code saveCard} + 通过 {@code cardEmitter} 推 {@code card} 事件；
     * LLM 失败会自动重试一次，仍失败则通过 {@link #emitRegenFailure} 推错误文案。
     * 最终 {@code sink.tryEmitComplete()} 推进 SSE 流。
     * </p>
     */
    public void regenerateCardAsync(ChatCardEntity original, Consumer<ChatCard> cardEmitter,
                                    Sinks.Many<ServerSentEvent<Object>> sink,
                                    AtomicBoolean doneRef,
                                    String conversationId, String agentCode) {
        CompletableFuture.runAsync(() -> {
            long t0 = System.currentTimeMillis();
            String cardType = original.getCardType();
            String toolCode = resolveRegenToolCode(cardType);
            log.info("[REGEN-PERF] regenerateCardAsync (async) START: t0={}, cardType={}, toolCode={}, conversationId={}, agentCode={}",
                    t0, cardType, toolCode, conversationId, agentCode);
            try {
                if (toolCode == null) {
                    log.warn("Unsupported cardType for LLM regenerate: {}", cardType);
                    emitRegenFailure(sink, "暂不支持重新生成该类型计划，请稍后重试或换一种表达");
                    return;
                }
                Double temperature = 0.95;
                String systemPrompt = buildRegenSystemPrompt(cardType);
                String regeneratePrompt = buildRegenUserPrompt(cardType, original.getPayload());
                long t1 = System.currentTimeMillis();
                log.info("[REGEN-PERF] prompt construction: {}ms", t1 - t0);

                boolean llmSuccess = false;
                for (int attempt = 1; attempt <= 2; attempt++) {
                    long t2 = System.currentTimeMillis();
                    log.info("[REGEN-PERF] LLM call START (attempt {}/2, httpTimeout=factory)", attempt);
                    llmSuccess = tryRegenerateViaLlm(
                            toolCode, systemPrompt, regeneratePrompt, temperature,
                            conversationId, agentCode, cardEmitter, original.getUserId());
                    long t3 = System.currentTimeMillis();
                    log.info("[REGEN-PERF] LLM call END (attempt {}/2): {}ms, success={}",
                            attempt, t3 - t2, llmSuccess);
                    if (llmSuccess) {
                        break;
                    }
                    if (attempt < 2) {
                        log.warn("LLM regeneration attempt {} failed, retrying once", attempt);
                    }
                }
                if (!llmSuccess) {
                    log.warn("LLM regeneration failed after 2 attempts, emitting error message");
                    emitRegenFailure(sink, "重新生成计划失败，请稍后重试");
                }
            } catch (Exception e) {
                log.error("regenerateCardAsync failed: {}", e.getMessage(), e);
                emitRegenFailure(sink, "重新生成计划失败，请稍后重试");
            } finally {
                long tEnd = System.currentTimeMillis();
                log.info("[REGEN-PERF] regenerateCardAsync END: total={}ms", tEnd - t0);
                if (doneRef.compareAndSet(false, true)) {
                    sink.tryEmitComplete();
                    log.info("[REGEN-PERF] sink.tryEmitComplete done: {}ms after async start", tEnd - t0);
                }
            }
        });
    }

    /**
     * 向 SSE 推送重新生成失败的提示文案（event=message）。
     */
    private void emitRegenFailure(Sinks.Many<ServerSentEvent<Object>> sink, String message) {
        try {
            sink.tryEmitNext(ServerSentEvent.<Object>builder()
                    .event("message")
                    .data(Map.of("text", message))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to emit regen failure message: {}", e.getMessage());
        }
    }

    /**
     * cardType → ToolPort tool_code（约定 {@code propose_<cardType>}）；未知类型返回 null。
     */
    private static String resolveRegenToolCode(String cardType) {
        if (cardType == null || cardType.isBlank()) {
            return null;
        }
        return "propose_" + cardType;
    }

    /** 构造 reject 重生成的系统提示词。 */
    private static String buildRegenSystemPrompt(String cardType) {
        return "请直接调用与卡片类型「" + cardType + "」对应的工具重新生成方案，不要输出解释性文本。";
    }

    /** 构造 reject 重生成的用户提示词（含原 payload 摘要）。 */
    private static String buildRegenUserPrompt(String cardType, Map<String, Object> origPayload) {
        Map<String, Object> p = origPayload != null ? origPayload : Map.of();
        return "用户对原方案不满意（卡片类型=" + cardType + "，原 payload=" + p
                + "）。请立即调用工具重新生成，不要输出任何文字说明。";
    }

    /**
     * 尝试通过 LLM + card tool 重新产出卡片（无超时，同步阻塞）。
     *
     * @param toolCode     ToolPort 中的 tool_code
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户引导消息
     * @param temperature  温度参数
     * @return true 如果 LLM 调用成功（无论是否触发了 tool），false 表示调用失败
     */
    private boolean tryRegenerateViaLlm(
            String toolCode,
            String systemPrompt, String userPrompt,
            Double temperature,
            String conversationId,
            String agentCode,
            Consumer<ChatCard> cardEmitter,
            String userId) {
        long t0 = System.currentTimeMillis();
        try {
            org.springframework.ai.openai.OpenAiChatOptions.Builder optionsBuilder =
                    org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .temperature(temperature);
            long t1 = System.currentTimeMillis();
            log.info("[REGEN-PERF] OpenAiChatOptions.builder: {}ms", t1 - t0);

            org.springframework.ai.chat.client.ChatClient chatClient =
                    getCachedChatClient(conversationId, agentCode);
            long t2 = System.currentTimeMillis();
            log.info("[REGEN-PERF] getCachedChatClient: {}ms", t2 - t1);

            List<org.springframework.ai.tool.ToolCallback> cardToolCallbacks =
                    toolPort.resolveToolCallbacksByCode(toolCode);
            String modelCode = resolveModelCode(conversationId, agentCode);
            java.util.Map<String, Object> toolCtxMap = new java.util.HashMap<>();
            toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.USER_ID, userId);
            toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.CONVERSATION_ID,
                    conversationId != null ? conversationId : "");
            if (modelCode != null) {
                toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.MODEL_CODE, modelCode);
            }
            toolCtxMap.put(org.deepstack.ai.kernel.tool.ToolContextKeys.CARD_EMITTER, cardEmitter);

            long t3 = System.currentTimeMillis();
            log.info("[REGEN-PERF] tool ready (resolveToolCallbacksByCode={}): {}ms, count={}",
                    toolCode, t3 - t2, cardToolCallbacks.size());

            log.info("[REGEN-PERF] chatClient.prompt().call() START (blocking until HTTP read timeout)");
            chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(optionsBuilder)
                    .tools(cardToolCallbacks)
                    .toolContext(toolCtxMap)
                    .call()
                    .chatResponse();
            long t4 = System.currentTimeMillis();
            log.info("[REGEN-PERF] chatClient.prompt().call() END: {}ms", t4 - t3);

            return true;
        } catch (Exception e) {
            long t4 = System.currentTimeMillis();
            log.warn("[REGEN-PERF] tryRegenerateViaLlm FAILED after {}ms: {}", t4 - t0, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 按 conversationId/agentCode 解析 modelCode，经 Factory 取当前 ChatModel 再包 ChatClient。
     * <p>
     * 不缓存 ChatClient：外壳会握死当时的 ChatModel，模型/超时变更后 regenerate 会打到旧实例。
     * Factory 自身已有实例缓存 + {@code AiModelChangedEvent} 失效。
     * </p>
     *
     * @param conversationId 业务会话 ID
     * @param agentCode      Agent 编码（会话反查失败时兜底）
     * @return 对应 Agent 的 ChatClient
     */
    private org.springframework.ai.chat.client.ChatClient getCachedChatClient(
            String conversationId, String agentCode) {
        String modelCode = resolveModelCode(conversationId, agentCode);
        if (modelCode == null || modelCode.isBlank()) {
            log.warn("getCachedChatClient 无法解析模型: conversationId={}, agentCode={}",
                    conversationId, agentCode);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "无法解析会话对应模型: conversationId=" + conversationId
                            + ", agentCode=" + agentCode);
        }
        org.springframework.ai.openai.OpenAiChatModel chatModel =
                aiChatClientFactory.getChatModel(modelCode);
        log.debug("getCachedChatClient: conversationId={}, agentCode={}, modelCode={}",
                conversationId, agentCode, modelCode);
        return org.springframework.ai.chat.client.ChatClient.builder(chatModel).build();
    }

    /**
     * 按 conversationId 反查 scene → modelCode；失败时用 agentCode 兜底；均缺失时返回 null。
     */
    private String resolveModelCode(String conversationId, String agentCode) {
        if (!isBlank(conversationId)) {
            String resolvedScene = conversationSceneResolver.resolveAgentCode(conversationId);
            if (resolvedScene != null) {
                String modelCode = agentModelResolver.resolveModelCodeByAgentCode(resolvedScene);
                if (modelCode != null) {
                    return modelCode;
                }
            }
        }
        if (!isBlank(agentCode)) {
            return agentModelResolver.resolveModelCodeByAgentCode(agentCode);
        }
        return null;
    }

    /** 空串判定（null / blank）。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
