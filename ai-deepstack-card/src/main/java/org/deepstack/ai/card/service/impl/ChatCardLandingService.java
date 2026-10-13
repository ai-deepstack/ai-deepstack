package org.deepstack.ai.card.service.impl;

import org.deepstack.ai.card.mapper.ChatCardActionMapper;
import org.deepstack.ai.card.mapper.ChatCardMapper;
import org.deepstack.ai.card.model.dto.request.CardActionRequest;
import org.deepstack.ai.card.model.entity.ChatCardActionEntity;
import org.deepstack.ai.card.model.entity.ChatCardEntity;
import org.deepstack.ai.card.service.spi.CardProcessor;
import org.deepstack.ai.card.service.spi.CardProcessorRegistry;
import org.deepstack.ai.kernel.enums.card.CardActionTypeEnum;
import org.deepstack.ai.kernel.enums.card.ChatCardStatusEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * 卡片动作落地服务。
 * <p>
 * 只负责 confirm / edit / reject 三个动作“已经校验通过之后”的持久化落点：调用
 * {@link CardProcessor#persist} 落地业务、以乐观锁推进卡片状态、写入动作历史。
 * 事务边界由 {@link TransactionTemplate} 显式圈定，保证 persist 与状态流转同生共死。
 * </p>
 * <p>
 * 与 {@link ChatCardServiceImpl} 的分工：外层服务只做鉴权/状态校验、动作分派以及 SSE/同步响应编排，
 * 不关心“落到库里”的细节；此处只关心一次已校验动作如何真正落库，不感知流式或同步路径，
 * 也不感知 HITL resume 与 LLM 重生成。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCardLandingService {

    private final ChatCardMapper chatCardMapper;
    private final ChatCardActionMapper chatCardActionMapper;
    private final CardProcessorRegistry cardProcessorRegistry;
    private final TransactionTemplate transactionTemplate;

    /**
     * confirm 落地：persist → PENDING→CONFIRMED → 写历史。
     * persist 异常原样抛出；乐观锁失败抛 {@link BusinessException}。
     */
    public Object landConfirm(ChatCardEntity entity, String operatorId) {
        CardProcessor processor = cardProcessorRegistry.get(entity.getCardType());
        // persist 与状态流转 / 历史必须同一事务：persist 抛异常时不能留下已改的状态
        return transactionTemplate.execute(status -> {
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
        });
    }

    /**
     * edit 落地：校验 → PENDING→EDITED → 写 payload/历史 → persist → EDITED→CONFIRMED。
     */
    public Object landEdit(ChatCardEntity entity, CardActionRequest request) {
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
        // 校验通过后，状态流转 / payload / 历史 / persist 必须同一事务，
        // 否则 persist 失败会把卡片留在 EDITED 半成品状态
        return transactionTemplate.execute(status -> {
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
        });
    }

    /**
     * reject 落地：PENDING→REJECTED → 写历史（不含 LLM 重生成 / HITL）。
     *
     * @param perfStartMs 流式路径传入以打 REGEN-PERF；同步传 null
     * @return 写历史完成时的时间戳（供后续 REGEN-PERF 计算）
     */
    public long landReject(ChatCardEntity entity, String operatorId, Long perfStartMs) {
        Long committedAt = transactionTemplate.execute(status -> {
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
        });
        return committedAt != null ? committedAt : System.currentTimeMillis();
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
}
