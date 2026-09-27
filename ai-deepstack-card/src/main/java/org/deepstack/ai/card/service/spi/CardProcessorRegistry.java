package org.deepstack.ai.card.service.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 卡片处理器注册表
 * <p>
 * Spring 启动时自动发现所有 {@link CardProcessor} Bean 并按 {@link CardProcessor#cardType()}
 * 注册。运行时按 cardType 查找对应处理器。
 * </p>
 *
 */
@Slf4j
@Component
public class CardProcessorRegistry {

    /**
     * 未注册 cardType 时的透传处理器：不落业务，仅返回 accepted=true。
     * 宿主应用应按类型注册专用 {@link CardProcessor} 覆盖。
     */
    private static final CardProcessor PASSTHROUGH = new CardProcessor() {
        /** 通配类型标识（非真实业务类型）。 */
        @Override
        public String cardType() {
            return "*";
        }

        /** 透传落地：无副作用，返回 accepted 标记。 */
        @Override
        public Object persist(Map<String, Object> payload, String userId) {
            return Map.of("accepted", true);
        }
    };

    private final Map<String, CardProcessor> processors;

    /**
     * Spring 自动注入所有 CardProcessor 实现
     */
    public CardProcessorRegistry(List<CardProcessor> allProcessors) {
        this.processors = allProcessors.stream()
                .collect(Collectors.toMap(
                        CardProcessor::cardType,
                        p -> p,
                        (existing, duplicate) -> {
                            log.warn("Duplicate CardProcessor for cardType '{}': keeping {}, ignoring {}",
                                    existing.cardType(), existing.getClass().getName(), duplicate.getClass().getName());
                            return existing;
                        },
                        ConcurrentHashMap::new
                ));
        log.info("CardProcessorRegistry: registered {} card processors: {}",
                processors.size(), processors.keySet());
    }

    /**
     * 按 cardType 获取处理器；未注册时回退到透传处理器（仅确认，不落业务）。
     *
     * @param cardType 卡片类型编码
     * @return 对应处理器，未注册时为透传实现
     */
    public CardProcessor get(String cardType) {
        CardProcessor processor = processors.get(cardType);
        if (processor == null) {
            log.warn("CardProcessorRegistry: miss cardType={}, fallback PASSTHROUGH", cardType);
            return PASSTHROUGH;
        }
        log.debug("CardProcessorRegistry: resolved cardType={}", cardType);
        return processor;
    }

    /**
     * 判断是否支持某卡片类型（已注册专用处理器）。
     *
     * @param cardType 卡片类型编码
     * @return 已注册则为 true
     */
    public boolean supports(String cardType) {
        boolean ok = processors.containsKey(cardType);
        log.debug("CardProcessorRegistry.supports: cardType={}, result={}", cardType, ok);
        return ok;
    }
}
