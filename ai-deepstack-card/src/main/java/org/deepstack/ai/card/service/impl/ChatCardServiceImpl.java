package org.deepstack.ai.card.service.impl;

import org.deepstack.ai.card.model.dto.request.CardActionRequest;
import org.deepstack.ai.card.model.dto.response.CardActionResponse;
import org.deepstack.ai.card.model.dto.response.ChatCard;
import org.deepstack.ai.card.service.spi.CardProcessor;
import org.deepstack.ai.card.service.spi.CardProcessorRegistry;
import org.deepstack.ai.card.model.entity.ChatCardActionEntity;
import org.deepstack.ai.card.model.entity.ChatCardEntity;
import org.deepstack.ai.card.mapper.ChatCardActionMapper;
import org.deepstack.ai.card.mapper.ChatCardMapper;
import org.deepstack.ai.card.service.ChatCardService;
import org.deepstack.ai.kernel.enums.card.CardActionTypeEnum;
import org.deepstack.ai.kernel.enums.card.ChatCardStatusEnum;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import java.util.Objects;
import org.deepstack.ai.card.spi.AgentModelResolver;
import org.deepstack.ai.card.spi.ConversationAgentResolver;
import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.runtime.RunContext;
import org.deepstack.ai.runtime.spi.HitlPort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 对话卡片服务实现。
 * <p>
 * confirm/edit/reject 先落地卡片状态；若卡片带 threadId 且 {@link HitlPort} 可用，则恢复图执行。
 * 无 threadId 时保持仅落地（reject 仍走 LLM 重生成）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCardServiceImpl implements ChatCardService {

    private static final int DEFAULT_EXPIRE_MINUTES = 30;
    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final ChatCardMapper chatCardMapper;
    private final ChatCardActionMapper chatCardActionMapper;
    private final CardProcessorRegistry cardProcessorRegistry;
    private final org.deepstack.ai.infra.llm.AiChatClientFactory aiChatClientFactory;
    private final AgentModelResolver agentModelResolver;
    private final ConversationAgentResolver conversationSceneResolver;
    private final org.deepstack.ai.runtime.spi.ToolPort toolPort;
    private final ObjectProvider<HitlPort> hitlPort;

    /**
     * 持久化对话卡片（生成 cardId / 默认 PENDING / 过期时间）。
     *
     * @param card 卡片 DTO
     * @return 落库后的卡片
     */
    @Override
    public ChatCard saveCard(ChatCard card) {
        log.info("saveCard: cardType={}, userId={}, conversationId={}",
                card.getCardType(), card.getUserId(), card.getConversationId());
        ChatCardEntity entity = toEntity(card);
        if (!StringUtils.hasText(entity.getThreadId())) {
            String tid = RunContext.peekThreadId();
            if (StringUtils.hasText(tid)) {
                entity.setThreadId(tid);
                card.setThreadId(tid);
            }
        }
        if (entity.getCardId() == null) {
            entity.setCardId("card_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        if (entity.getStatus() == null) {
            entity.setStatus(ChatCardStatusEnum.PENDING.getCode());
        }
        if (entity.getExpiresAt() == null) {
            entity.setExpiresAt(LocalDateTime.now().plusMinutes(DEFAULT_EXPIRE_MINUTES));
        }
        chatCardMapper.insert(entity);
        log.info("ChatCard saved: cardId={}, cardType={}, userId={}, conversationId={}",
                entity.getCardId(), entity.getCardType(), entity.getUserId(), entity.getConversationId());
        return toDto(entity);
    }

    /**
     * 按 cardId 查询未删除卡片。
     *
     * @param cardId 卡片业务 ID
     * @return 卡片 DTO
     */
    @Override
    public ChatCard getByCardId(String cardId) {
        log.info("getByCardId: cardId={}", cardId);
        ChatCardEntity entity = chatCardMapper.selectOne(
                new LambdaQueryWrapper<ChatCardEntity>()
                        .eq(ChatCardEntity::getCardId, cardId)
                        .eq(ChatCardEntity::getIsDel, 0));
        if (entity == null) {
            log.warn("卡片不存在: cardId={}", cardId);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(), "卡片不存在: " + cardId);
        }
        return toDto(entity);
    }

    /**
     * SSE 流式执行卡片动作（confirm / edit / reject）。
     *
     * @param request 动作请求
     * @return SSE 事件流
     */
    @Override
    public Flux<ServerSentEvent<Object>> actStream(CardActionRequest request) {
        log.info("ChatCard actStream: cardId={}, action={}, userId={}",
                request.getCardId(), request.getAction(), request.getUserId());

        ChatCardEntity entity = chatCardMapper.selectOne(
                new LambdaQueryWrapper<ChatCardEntity>()
                        .eq(ChatCardEntity::getCardId, request.getCardId())
                        .eq(ChatCardEntity::getIsDel, 0));
        if (entity == null) {
            log.warn("actStream 卡片不存在: cardId={}", request.getCardId());
            return emitError("卡片不存在: " + request.getCardId());
        }

        // 校验卡片归属（null-safe：防止 entity.userId 为 null 时 NPE）
        if (entity.getUserId() == null || !entity.getUserId().equals(request.getUserId())) {
            log.warn("actStream 无权操作: cardId={}, userId={}", request.getCardId(), request.getUserId());
            return emitError("无权操作此卡片");
        }

        // 校验状态
        if (!Objects.equals(entity.getStatus(), ChatCardStatusEnum.PENDING.getCode())) {
            log.warn("actStream 状态不允许: cardId={}, status={}", entity.getCardId(), entity.getStatus());
            return emitError("卡片状态不允许操作: " + ChatCardStatusEnum.labelOf(entity.getStatus()));
        }

        // 校验过期
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            int affected = chatCardMapper.updateStatusIfMatch(entity.getCardId(), ChatCardStatusEnum.PENDING.getCode(), ChatCardStatusEnum.EXPIRED.getCode());
            if (affected > 0) {
                log.info("ChatCard expired: cardId={}", entity.getCardId());
            }
            return emitError("卡片已过期");
        }

        CardActionTypeEnum action = CardActionTypeEnum.of(request.getAction());
        log.info("actStream 分支: cardId={}, action={}", entity.getCardId(), action);
        if (action == null) {
            log.warn("actStream 不支持的动作: cardId={}, action={}", entity.getCardId(), request.getAction());
            return emitError("不支持的动作: " + request.getAction());
        }
        return switch (action) {
            case CONFIRM -> handleConfirmStream(entity, request);
            case EDIT -> handleEditStream(entity, request);
            case REJECT -> handleRejectStream(entity, request);
        };
    }

    /**
     * 同步执行卡片动作（confirm / edit / reject）。
     *
     * @param request 动作请求
     * @return 动作结果
     */
    @Override
    public CardActionResponse act(CardActionRequest request) {
        log.info("ChatCard act (sync): cardId={}, action={}", request.getCardId(), request.getAction());

        ChatCardEntity entity = chatCardMapper.selectOne(
                new LambdaQueryWrapper<ChatCardEntity>()
                        .eq(ChatCardEntity::getCardId, request.getCardId())
                        .eq(ChatCardEntity::getIsDel, 0));
        if (entity == null) {
            log.warn("act 卡片不存在: cardId={}", request.getCardId());
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(), "卡片不存在: " + request.getCardId());
        }
        if (entity.getUserId() == null || !entity.getUserId().equals(request.getUserId())) {
            log.warn("act 无权操作: cardId={}, userId={}", request.getCardId(), request.getUserId());
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED.getCode(), "无权操作此卡片");
        }
        if (!Objects.equals(entity.getStatus(), ChatCardStatusEnum.PENDING.getCode())) {
            log.warn("act 状态不允许: cardId={}, status={}", entity.getCardId(), entity.getStatus());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(),
                    "卡片状态不允许操作: " + ChatCardStatusEnum.labelOf(entity.getStatus()));
        }

        CardActionTypeEnum action = CardActionTypeEnum.of(request.getAction());
        log.info("act 分支: cardId={}, action={}", entity.getCardId(), action);
        if (action == null) {
            log.warn("act 不支持的动作: cardId={}, action={}", entity.getCardId(), request.getAction());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(),
                    "不支持的动作: " + request.getAction());
        }
        return switch (action) {
            case CONFIRM -> handleConfirmSync(entity, request);
            case EDIT -> handleEditSync(entity, request);
            case REJECT -> handleRejectSync(entity, request);
        };
    }

    /**
     * 列出用户已确认的历史卡片。
     *
     * @param userId   用户 ID
     * @param cardType 卡片类型（可空）
     * @param limit    条数上限
     * @return 卡片列表
     */
    @Override
    public List<ChatCard> listConfirmedCards(String userId, String cardType, int limit) {
        log.info("listConfirmedCards: userId={}, cardType={}, limit={}", userId, cardType, limit);
        if (limit <= 0) {
            limit = DEFAULT_HISTORY_LIMIT;
        }
        LambdaQueryWrapper<ChatCardEntity> wrapper = new LambdaQueryWrapper<ChatCardEntity>()
                .eq(ChatCardEntity::getUserId, userId)
                .eq(ChatCardEntity::getStatus, ChatCardStatusEnum.CONFIRMED.getCode())
                .eq(ChatCardEntity::getIsDel, 0)
                .orderByDesc(ChatCardEntity::getCreateTime)
                .last("LIMIT " + limit);
        if (cardType != null && !cardType.isEmpty()) {
            wrapper.eq(ChatCardEntity::getCardType, cardType);
        }
        List<ChatCardEntity> entities = chatCardMapper.selectList(wrapper);
        List<ChatCard> cards = new ArrayList<>();
        for (ChatCardEntity e : entities) {
            cards.add(toDto(e));
        }
        log.info("listConfirmedCards done: userId={}, count={}", userId, cards.size());
        return cards;
    }

    /**
     * 校验 cardType 已注册后，原样拷贝 params 作为 payload。
     *
     * @param cardType 卡片类型
     * @param params   业务参数
     * @return payload Map
     */
    @Override
    public Map<String, Object> buildPayload(String cardType, Map<String, Object> params) {
        log.info("buildPayload: cardType={}, paramKeys={}",
                cardType, params != null ? params.keySet() : null);
        // 校验 cardType 已注册
        cardProcessorRegistry.get(cardType);
        return new LinkedHashMap<>(params);
    }

    // ===== 动作处理（流式） =====

    /**
     * 确认卡片：调用 CardProcessor.persist 落地业务，并乐观锁更新卡片状态 PENDING → CONFIRMED。
     * <p>
     * 生产级加固点：
     * <ul>
     *   <li>幂等：通过乐观锁 {@code updateStatusIfMatch(cardId, 'PENDING', 'CONFIRMED')} 保证只有第一个
     *       confirm 请求能成功；重复请求或并发请求会返回错误，不会重复落地业务。</li>
     *   <li>事务：persist + 状态更新 + action 历史在同一事务内，失败回滚。</li>
     *   <li>错误事件：persist 失败、乐观锁失败都会通过 SSE {@code error} 事件返回。</li>
     * </ul>
     *
     * @param entity  卡片实体
     * @param request 动作请求
     * @return SSE 事件流
     */
    @Transactional(rollbackFor = Exception.class)
    protected Flux<ServerSentEvent<Object>> handleConfirmStream(ChatCardEntity entity, CardActionRequest request) {
        log.info("handleConfirmStream: cardId={}, cardType={}", entity.getCardId(), entity.getCardType());
        Object bizResult;
        try {
            bizResult = landConfirm(entity, request.getUserId());
        } catch (BusinessException e) {
            return emitError(e.getMessage());
        } catch (Exception e) {
            log.error("ChatCard confirm persist failed: cardId={}, cardType={}, error={}",
                    entity.getCardId(), entity.getCardType(), e.getMessage(), e);
            return emitError("计划落地失败: " + e.getMessage());
        }
        return emitLandedActionFlux(entity, request, CardActionTypeEnum.CONFIRM,
                ChatCardStatusEnum.CONFIRMED, bizResult, "已确认。", entity.getConversationId());
    }

    /**
     * 编辑卡片：用户修改 payload 后重新生成方案并落地。
     * <p>
     * 加固点：payload 校验、乐观锁状态切换、事务保证。
     * edit 路径切到 EDITED 中间态后再切到 CONFIRMED，两步都用乐观锁。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    private Flux<ServerSentEvent<Object>> handleEditStream(ChatCardEntity entity, CardActionRequest request) {
        log.info("handleEditStream: cardId={}, cardType={}", entity.getCardId(), entity.getCardType());
        Object bizResult;
        try {
            bizResult = landEdit(entity, request);
        } catch (BusinessException e) {
            return emitError(e.getMessage());
        } catch (Exception e) {
            log.error("ChatCard edit persist failed: cardId={}, error={}",
                    entity.getCardId(), e.getMessage(), e);
            return emitError("计划落地失败: " + e.getMessage());
        }
        return emitLandedActionFlux(entity, request, CardActionTypeEnum.EDIT,
                ChatCardStatusEnum.CONFIRMED, bizResult, "已按你的修改确认。", entity.getConversationId());
    }

    /**
     * 拒绝卡片：乐观锁切到 REJECTED，调 LLM 重新产出新卡片。
     * <p>
     * SSE 流结构：intro → [等待 LLM] → card → card_action_result → done。
     * LLM 成功 → tool 内部 saveCard + emit {@code card} 事件；
     * LLM 失败会自动重试一次，仍失败则 emit {@code message} 错误文案（不再使用本地假数据）。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    private Flux<ServerSentEvent<Object>> handleRejectStream(ChatCardEntity entity, CardActionRequest request) {
        long t0 = System.currentTimeMillis();
        log.info("[REGEN-PERF] handleRejectStream START: cardId={}, t0={}", entity.getCardId(), t0);

        long t2;
        try {
            t2 = landReject(entity, request.getUserId(), t0);
        } catch (BusinessException e) {
            return emitError(e.getMessage());
        }

        if (canResumeHitl(entity, request)) {
            String doneConversationId = firstNonBlank(entity.getConversationId(), request.getConversationId());
            return emitLandedActionFlux(entity, request, CardActionTypeEnum.REJECT,
                    ChatCardStatusEnum.REJECTED, null, "已拒绝，流程已继续。",
                    doneConversationId != null ? doneConversationId : "");
        }

        // 卡片落库时可能 conversationId 为空：优先实体，其次请求回传
        String conversationId = firstNonBlank(entity.getConversationId(), request.getConversationId());
        String agentCode = firstNonBlank(request.getAgentCode(), null);
        if (isBlank(entity.getConversationId()) && !isBlank(conversationId)) {
            entity.setConversationId(conversationId);
        }

        log.info("ChatCard reject: regenerating via LLM, userId={}, conversationId={}, agentCode={}, cardType={}",
                entity.getUserId(), conversationId, agentCode, entity.getCardType());

        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().unicast().onBackpressureBuffer();
        java.util.concurrent.atomic.AtomicReference<ChatCard> newCardRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean doneRef = new java.util.concurrent.atomic.AtomicBoolean(false);

        // cardEmitter: LLM 成功时由 tool 调用，统一 emit "card" 事件
        Consumer<ChatCard> cardEmitter = card -> {
            long emitStart = System.currentTimeMillis();
            newCardRef.set(card);
            try {
                sink.tryEmitNext(ServerSentEvent.<Object>builder()
                        .event("card")
                        .data(card)
                        .build());
                log.info("[REGEN-PERF] cardEmitter.emit: {}ms (cardId={})",
                        System.currentTimeMillis() - emitStart, card.getCardId());
            } catch (Exception e) {
                log.warn("Failed to emit regenerated card: {}", e.getMessage());
            }
        };

        // 异步触发 LLM 重新产出（失败重试一次，仍失败返回错误文案）
        regenerateCardAsync(entity, cardEmitter, sink, doneRef, conversationId, agentCode);
        long t3 = System.currentTimeMillis();
        log.info("[REGEN-PERF] regenerateCardAsync dispatched: {}ms (async, non-blocking)", t3 - t2);

        // SSE 流结构：intro → [等待 LLM] → card → card_action_result → done
        String intro = "好的，我重新为你制定计划 👇";
        final String doneConversationId = conversationId != null ? conversationId : "";
        return Flux.just(
                ServerSentEvent.<Object>builder()
                        .event("message")
                        .data(Map.of("text", intro))
                        .build()
        ).concatWith(sink.asFlux())
         .concatWith(Flux.defer(() -> {
             long t4 = System.currentTimeMillis();
             log.info("[REGEN-PERF] SSE concatWith (post-sink) reached: {}ms after dispatch", t4 - t3);
             ChatCard newCard = newCardRef.get();
             if (newCard != null) {
                 return Flux.just(ServerSentEvent.<Object>builder()
                         .event("card_action_result")
                         .data(Map.of(
                                 "cardId", entity.getCardId(),
                                 "status", ChatCardStatusEnum.REJECTED.getCode(),
                                 "statusName", ChatCardStatusEnum.REJECTED.getLabel(),
                                 "newCardId", newCard.getCardId()))
                         .build(),
                         ServerSentEvent.<Object>builder()
                         .event("done")
                         .data(Map.of("conversationId", doneConversationId))
                         .build());
             }
             return Flux.just(ServerSentEvent.<Object>builder()
                     .event("card_action_result")
                     .data(Map.of(
                             "cardId", entity.getCardId(),
                             "status", ChatCardStatusEnum.REJECTED.getCode(),
                             "statusName", ChatCardStatusEnum.REJECTED.getLabel()))
                     .build(),
                     ServerSentEvent.<Object>builder()
                     .event("done")
                     .data(Map.of("conversationId", doneConversationId))
                     .build());
         }));
    }

    /**
     * 异步通过 LLM + card tool 重新产出卡片。
     * <p>
     * LLM 成功 → tool 内部 {@code saveCard} + 通过 {@code cardEmitter} 推 {@code card} 事件；
     * LLM 失败会自动重试一次，仍失败则通过 {@link #emitRegenFailure} 推错误文案。
     * 最终 {@code sink.tryEmitComplete()} 推进 SSE 流。
     * </p>
     */
    private void regenerateCardAsync(ChatCardEntity original, Consumer<ChatCard> cardEmitter,
                                      Sinks.Many<ServerSentEvent<Object>> sink,
                                      java.util.concurrent.atomic.AtomicBoolean doneRef,
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
            java.util.function.Consumer<org.deepstack.ai.card.model.dto.response.ChatCard> cardEmitter,
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
     * @param agentCode      智能体编码（会话反查失败时兜底）
     * @return 对应智能体的 ChatClient
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

    /** 取首个非空串；两者皆空则返回 null。 */
    private static String firstNonBlank(String primary, String fallback) {
        if (!isBlank(primary)) {
            return primary;
        }
        return isBlank(fallback) ? null : fallback;
    }

    // ===== 动作处理（非流式） =====
    // 与流式版本对等加固：乐观锁 + 事务 + 校验。

    /**
     * 同步确认卡片并落地业务。
     *
     * @param entity  卡片实体
     * @param request 动作请求
     * @return 动作结果
     */
    @Transactional(rollbackFor = Exception.class)
    protected CardActionResponse handleConfirmSync(ChatCardEntity entity, CardActionRequest request) {
        log.info("handleConfirmSync: cardId={}, cardType={}", entity.getCardId(), entity.getCardType());
        Object bizResult;
        try {
            bizResult = landConfirm(entity, request.getUserId());
        } catch (Exception e) {
            if (!(e instanceof BusinessException)) {
                log.error("ChatCard confirm persist failed (sync): cardId={}, cardType={}",
                        entity.getCardId(), entity.getCardType(), e);
            }
            throw e;
        }
        return withResume(new CardActionResponse(entity.getCardId(), ChatCardStatusEnum.CONFIRMED.getCode(),
                        ChatCardStatusEnum.CONFIRMED.getLabel(), bizResult),
                entity, request, CardActionTypeEnum.CONFIRM.wireValue());
    }

    /**
     * 同步编辑卡片：校验 payload → PENDING→EDITED→persist→CONFIRMED。
     *
     * @param entity  卡片实体
     * @param request 动作请求（须含 modifiedPayload）
     * @return 动作结果
     */
    @Transactional(rollbackFor = Exception.class)
    protected CardActionResponse handleEditSync(ChatCardEntity entity, CardActionRequest request) {
        log.info("handleEditSync: cardId={}, cardType={}", entity.getCardId(), entity.getCardType());
        Object bizResult = landEdit(entity, request);
        return withResume(new CardActionResponse(entity.getCardId(), ChatCardStatusEnum.CONFIRMED.getCode(),
                        ChatCardStatusEnum.CONFIRMED.getLabel(), bizResult),
                entity, request, CardActionTypeEnum.EDIT.wireValue());
    }

    /**
     * 同步拒绝卡片：乐观锁 PENDING→REJECTED（不触发 LLM 重生成）。
     *
     * @param entity  卡片实体
     * @param request 动作请求
     * @return 动作结果
     */
    @Transactional(rollbackFor = Exception.class)
    protected CardActionResponse handleRejectSync(ChatCardEntity entity, CardActionRequest request) {
        log.info("handleRejectSync: cardId={}, cardType={}", entity.getCardId(), entity.getCardType());
        landReject(entity, request.getUserId(), null);
        return withResume(new CardActionResponse(entity.getCardId(), ChatCardStatusEnum.REJECTED.getCode(),
                        ChatCardStatusEnum.REJECTED.getLabel(), null),
                entity, request, CardActionTypeEnum.REJECT.wireValue());
    }

    // ===== 落地核心（Stream / Sync 共用） =====

    /**
     * confirm 落地：persist → PENDING→CONFIRMED → 写历史。
     * persist 异常原样抛出；乐观锁失败抛 {@link BusinessException}。
     */
    private Object landConfirm(ChatCardEntity entity, String operatorId) {
        CardProcessor processor = cardProcessorRegistry.get(entity.getCardType());
        Object bizResult = processor.persist(entity.getPayload(), entity.getUserId());
        int affected = chatCardMapper.updateStatusIfMatch(
                entity.getCardId(), ChatCardStatusEnum.PENDING.getCode(), ChatCardStatusEnum.CONFIRMED.getCode());
        if (affected == 0) {
            log.warn("ChatCard confirm optimistic lock failed: cardId={}, currentStatus={}",
                    entity.getCardId(), entity.getStatus());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(),
                    "卡片已被处理或状态已变更，无法重复确认");
        }
        entity.setStatus(ChatCardStatusEnum.CONFIRMED.getCode());
        saveActionHistory(entity.getCardId(), CardActionTypeEnum.CONFIRM.getCode(), null, operatorId);
        return bizResult;
    }

    /**
     * edit 落地：校验 → PENDING→EDITED → 写 payload/历史 → persist → EDITED→CONFIRMED。
     */
    private Object landEdit(ChatCardEntity entity, CardActionRequest request) {
        if (request.getModifiedPayload() == null) {
            log.warn("edit 缺少 modifiedPayload: cardId={}", entity.getCardId());
            throw new BusinessException(CommonErrorCode.MISSING_PARAM.getCode(),
                    "edit 动作必须携带 modifiedPayload");
        }
        CardProcessor processor = cardProcessorRegistry.get(entity.getCardType());
        CardProcessor.ValidationResult validation = processor.validate(request.getModifiedPayload());
        if (!validation.valid()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "payload 校验失败: " + validation.errorMessage());
        }
        int affected = chatCardMapper.updateStatusIfMatch(
                entity.getCardId(), ChatCardStatusEnum.PENDING.getCode(), ChatCardStatusEnum.EDITED.getCode());
        if (affected == 0) {
            log.warn("ChatCard edit optimistic lock failed: cardId={}, currentStatus={}",
                    entity.getCardId(), entity.getStatus());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(),
                    "卡片已被处理或状态已变更，无法编辑");
        }
        entity.setStatus(ChatCardStatusEnum.EDITED.getCode());
        entity.setPayload(request.getModifiedPayload());
        chatCardMapper.updateById(entity);
        saveActionHistory(entity.getCardId(), CardActionTypeEnum.EDIT.getCode(),
                request.getModifiedPayload(), request.getUserId());

        Object bizResult = processor.persist(entity.getPayload(), entity.getUserId());
        int affected2 = chatCardMapper.updateStatusIfMatch(
                entity.getCardId(), ChatCardStatusEnum.EDITED.getCode(), ChatCardStatusEnum.CONFIRMED.getCode());
        if (affected2 == 0) {
            log.warn("ChatCard edit→confirm optimistic lock failed: cardId={}", entity.getCardId());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(),
                    "卡片状态变更异常，请重试");
        }
        entity.setStatus(ChatCardStatusEnum.CONFIRMED.getCode());
        return bizResult;
    }

    /**
     * reject 落地：PENDING→REJECTED → 写历史（不含 LLM 重生成 / HITL）。
     *
     * @param perfStartMs 流式路径传入以打 REGEN-PERF；同步传 null
     * @return 写历史完成时的时间戳（供后续 REGEN-PERF 计算）
     */
    private long landReject(ChatCardEntity entity, String operatorId, Long perfStartMs) {
        int affected = chatCardMapper.updateStatusIfMatch(
                entity.getCardId(), ChatCardStatusEnum.PENDING.getCode(), ChatCardStatusEnum.REJECTED.getCode());
        long t1 = System.currentTimeMillis();
        if (perfStartMs != null) {
            log.info("[REGEN-PERF] optimistic lock update (PENDING→REJECTED): {}ms, affected={}",
                    t1 - perfStartMs, affected);
        }
        if (affected == 0) {
            log.warn("ChatCard reject optimistic lock failed: cardId={}, currentStatus={}",
                    entity.getCardId(), entity.getStatus());
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(),
                    "卡片已被处理或状态已变更，无法重复拒绝");
        }
        entity.setStatus(ChatCardStatusEnum.REJECTED.getCode());
        saveActionHistory(entity.getCardId(), CardActionTypeEnum.REJECT.getCode(), null, operatorId);
        long t2 = System.currentTimeMillis();
        if (perfStartMs != null) {
            log.info("[REGEN-PERF] saveActionHistory: {}ms", t2 - t1);
        }
        return t2;
    }

    /**
     * 落地成功后的 SSE：message + card_action_result + done（可选附带 bizResult）。
     */
    private Flux<ServerSentEvent<Object>> emitLandedActionFlux(
            ChatCardEntity entity,
            CardActionRequest request,
            CardActionTypeEnum action,
            ChatCardStatusEnum status,
            Object bizResult,
            String defaultReply,
            String doneConversationId) {
        GraphRunResponse resumed = doResumeHitl(entity, request, action.wireValue());
        String reply = firstNonBlank(resumed != null ? resumed.getResult() : null, defaultReply);
        Map<String, Object> actionResult = new LinkedHashMap<>();
        actionResult.put("cardId", entity.getCardId());
        actionResult.put("status", status.getCode());
        actionResult.put("statusName", status.getLabel());
        if (bizResult != null || status == ChatCardStatusEnum.CONFIRMED) {
            actionResult.put("bizResult", bizResult != null ? bizResult : Map.of());
        }
        putResumeMeta(actionResult, resumed);
        return Flux.just(
                ServerSentEvent.<Object>builder()
                        .event("message")
                        .data(Map.of("text", reply))
                        .build(),
                ServerSentEvent.<Object>builder()
                        .event("card_action_result")
                        .data(actionResult)
                        .build(),
                ServerSentEvent.<Object>builder()
                        .event("done")
                        .data(Map.of("conversationId", doneConversationId))
                        .build()
        );
    }

    // ===== 辅助方法 =====

    /**
     * 同步动作响应附带 HITL resume 结果（若可恢复）。
     *
     * @param response 原动作响应
     * @param entity   卡片实体
     * @param request  动作请求
     * @param action   confirm / edit / reject
     * @return 可能已填充 threadId / graphStatus / graphResult 的响应
     */
    private CardActionResponse withResume(CardActionResponse response, ChatCardEntity entity,
                                         CardActionRequest request, String action) {
        GraphRunResponse resumed = doResumeHitl(entity, request, action);
        if (resumed != null) {
            response.setThreadId(resumed.getThreadId());
            Integer gs = resumed.getStatus();
            response.setGraphStatus(gs);
            response.setGraphStatusName(GraphRunStatusEnum.labelOf(gs));
            response.setGraphResult(resumed.getResult());
        }
        return response;
    }

    /**
     * 将 resume 结果写入流式 actionResult map。
     *
     * @param actionResult 待填充的结果 map
     * @param resumed      图恢复响应；null 则忽略
     */
    private static void putResumeMeta(Map<String, Object> actionResult, GraphRunResponse resumed) {
        if (resumed == null) {
            return;
        }
        actionResult.put("threadId", resumed.getThreadId());
        actionResult.put("graphStatus", resumed.getStatus());
        actionResult.put("graphStatusName", GraphRunStatusEnum.labelOf(resumed.getStatus()));
        actionResult.put("graphResult", resumed.getResult());
    }

    /**
     * 是否具备 HITL resume 条件：threadId 非空且 HitlPort 可用。
     *
     * @param entity  卡片
     * @param request 动作请求（可覆盖 threadId）
     * @return true 表示可尝试 resume
     */
    private boolean canResumeHitl(ChatCardEntity entity, CardActionRequest request) {
        String threadId = firstNonBlank(request != null ? request.getThreadId() : null,
                entity != null ? entity.getThreadId() : null);
        HitlPort port = hitlPort.getIfAvailable();
        return StringUtils.hasText(threadId) && port != null && port.available();
    }

    /**
     * 卡片状态落地后再 resume；无 threadId 或 HitlPort 不可用时跳过。
     */
    private GraphRunResponse doResumeHitl(ChatCardEntity entity, CardActionRequest request, String action) {
        if (!canResumeHitl(entity, request)) {
            log.debug("HITL skip resume: cardId={}, action={}", entity.getCardId(), action);
            return null;
        }
        String threadId = firstNonBlank(request.getThreadId(), entity.getThreadId());
        String conversationId = firstNonBlank(entity.getConversationId(), request.getConversationId());
        String agentCode = firstNonBlank(request.getAgentCode(), null);
        if (!StringUtils.hasText(agentCode) && StringUtils.hasText(conversationId)) {
            agentCode = conversationSceneResolver.resolveAgentCode(conversationId);
        }
        if (!StringUtils.hasText(agentCode)) {
            log.warn("HITL skip resume: no agentCode, cardId={}", entity.getCardId());
            return null;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(WorkflowState.CARD_ACTION, action);
        if (request.getModifiedPayload() != null) {
            updates.put(WorkflowState.CARD_MODIFIED_PAYLOAD, request.getModifiedPayload());
        }
        updates.put(WorkflowState.PENDING_CARDS, new ArrayList<>());
        GraphRunRequest base = new GraphRunRequest();
        base.setUserId(request.getUserId());
        base.setConversationId(conversationId);
        base.setAgentCode(agentCode);
        base.setThreadId(threadId);
        log.info("HITL resume after card action: cardId={}, action={}, threadId={}, agentCode={}",
                entity.getCardId(), action, threadId, agentCode);
        try {
            return hitlPort.getObject().resume(agentCode, threadId, updates, base);
        } catch (Exception e) {
            log.error("HITL resume failed: cardId={}, threadId={}", entity.getCardId(), threadId, e);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(),
                    "图恢复失败: " + e.getMessage());
        }
    }

    /** 写入卡片动作历史（confirm / edit / reject）。 */
    private void saveActionHistory(String cardId, Integer action, Map<String, Object> modifiedPayload,
                                   String operatorId) {
        log.debug("saveActionHistory: cardId={}, action={}, operatorId={}", cardId, action, operatorId);
        ChatCardActionEntity actionEntity = new ChatCardActionEntity();
        actionEntity.setCardId(cardId);
        actionEntity.setAction(action);
        actionEntity.setModifiedPayload(modifiedPayload);
        actionEntity.setOperatorId(operatorId);
        chatCardActionMapper.insert(actionEntity);
    }

    /** 构造 SSE error 事件流。 */
    private Flux<ServerSentEvent<Object>> emitError(String message) {
        log.debug("emitError: {}", message);
        return Flux.just(ServerSentEvent.<Object>builder()
                .event("error")
                .data(Map.of("message", message))
                .build());
    }

    // ===== Entity ↔ DTO 转换 =====

    /** DTO → 实体（新建时默认 PENDING）。 */
    private ChatCardEntity toEntity(ChatCard dto) {
        ChatCardEntity entity = new ChatCardEntity();
        entity.setCardId(dto.getCardId());
        entity.setConversationId(dto.getConversationId());
        entity.setThreadId(dto.getThreadId());
        entity.setCheckpointId(dto.getCheckpointId());
        entity.setUserId(dto.getUserId() != null ? dto.getUserId() : "");
        // userId on entity is required; copy from a separate field if needed
        entity.setCardType(dto.getCardType());
        entity.setTitle(dto.getTitle());
        entity.setPayload(dto.getPayload());
        entity.setActions(dto.getActions());
        entity.setStatus(ChatCardStatusEnum.PENDING.getCode());
        entity.setExpiresAt(dto.getExpiresAt());
        return entity;
    }

    /** 实体 → DTO。 */
    private ChatCard toDto(ChatCardEntity entity) {
        ChatCard dto = new ChatCard();
        dto.setCardId(entity.getCardId());
        dto.setCardType(entity.getCardType());
        dto.setTitle(entity.getTitle());
        dto.setConversationId(entity.getConversationId());
        dto.setUserId(entity.getUserId());
        dto.setThreadId(entity.getThreadId());
        dto.setCheckpointId(entity.getCheckpointId());
        dto.setPayload(entity.getPayload());
        dto.setActions(entity.getActions());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setCreatedAt(entity.getCreateTime());
        return dto;
    }
}
