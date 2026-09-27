package org.deepstack.ai.card.spi;

import org.deepstack.ai.runtime.spi.CardPort;
import org.deepstack.ai.runtime.spi.EmittedCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CardPort 钩子（{@link EmittedCard}，无业务侧 ChatCard 类型泄漏）。
 */
@Slf4j
@Component
public class CardPortAdapter implements CardPort {

    /**
     * 卡片产出后的 SPI 钩子（当前仅记录日志）。
     *
     * @param card 已产出的卡片摘要
     */
    @Override
    public void afterEmitted(EmittedCard card) {
        if (card == null) {
            log.warn("CardPort.afterEmitted 跳过: card 为空");
            return;
        }
        log.info("CardPort.afterEmitted: cardId={}, cardType={}",
                card.getCardId(), card.getCardType());
    }
}
