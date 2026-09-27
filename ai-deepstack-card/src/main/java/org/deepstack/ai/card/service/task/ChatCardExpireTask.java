package org.deepstack.ai.card.service.task;

import org.deepstack.ai.card.model.entity.ChatCardEntity;
import org.deepstack.ai.card.mapper.ChatCardMapper;
import org.deepstack.ai.kernel.enums.card.ChatCardStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对话卡片过期清理任务
 * <p>
 * 定时扫描 PENDING 状态但已过期的卡片，将其标记为 EXPIRED。
 * </p>
 * <p>
 * 生产化要点：
 * <ul>
 *   <li>分批扫描，避免单次查询过大（默认每批 200 条）</li>
 *   <li>使用 UPDATE WHERE 条件更新，避免先查后改的并发问题</li>
 *   <li>任务串行执行，避免多实例重复清理（依赖分布式锁或单实例部署）</li>
 * </ul>
 * </p>
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCardExpireTask {

    private static final int BATCH_SIZE = 200;

    private final ChatCardMapper chatCardMapper;

    /**
     * 每 5 分钟扫描一次过期卡片
     * <p>
     * cron: 秒 分 时 日 月 周。{@code 0 0/5 * * * ?} = 每 5 分钟整点执行
     * </p>
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void expirePendingCards() {
        LocalDateTime now = LocalDateTime.now();
        AtomicInteger totalExpired = new AtomicInteger(0);

        try {
            // 批量更新：把所有 PENDING 且已过期的卡片标记为 EXPIRED
            // 使用 UPDATE WHERE 避免先查后改的并发问题
            int batchExpired;
            do {
                // 分批：先查出这批的 cardId，再更新，避免一次 UPDATE 锁太多行
                LambdaQueryWrapper<ChatCardEntity> queryWrapper = new LambdaQueryWrapper<ChatCardEntity>()
                        .select(ChatCardEntity::getCardId)
                        .eq(ChatCardEntity::getStatus, ChatCardStatusEnum.PENDING.getCode())
                        .eq(ChatCardEntity::getIsDel, 0)
                        .lt(ChatCardEntity::getExpiresAt, now)
                        .last("LIMIT " + BATCH_SIZE);

                java.util.List<ChatCardEntity> batch = chatCardMapper.selectList(queryWrapper);
                if (batch.isEmpty()) {
                    break;
                }

                java.util.List<String> cardIds = batch.stream()
                        .map(ChatCardEntity::getCardId)
                        .toList();

                // update_time 由 DB 触发器自动刷新
                LambdaUpdateWrapper<ChatCardEntity> batchUpdate = new LambdaUpdateWrapper<ChatCardEntity>()
                        .in(ChatCardEntity::getCardId, cardIds)
                        .set(ChatCardEntity::getStatus, ChatCardStatusEnum.EXPIRED.getCode());

                batchExpired = chatCardMapper.update(null, batchUpdate);
                totalExpired.addAndGet(batchExpired);

                log.debug("ChatCardExpireTask: batch expired {} cards", batchExpired);
            } while (batchExpired > 0);

            if (totalExpired.get() > 0) {
                log.info("ChatCardExpireTask: expired {} PENDING cards (scanTime={})",
                        totalExpired.get(), now);
            }
        } catch (Exception e) {
            log.error("ChatCardExpireTask failed: {}", e.getMessage(), e);
        }
    }
}
