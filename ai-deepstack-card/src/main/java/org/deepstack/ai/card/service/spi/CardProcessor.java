package org.deepstack.ai.card.service.spi;

import java.util.Map;

/**
 * 卡片处理器 SPI
 * <p>
 * 每种卡片类型对应一个实现。新增类型只需实现本接口并注册为 Spring Bean，
 * {@link CardProcessorRegistry} 会自动发现并注册。
 * </p>
 */
public interface CardProcessor {

    /**
     * 卡片类型标识（与工具编码约定 {@code propose_<cardType>} 对齐）
     */
    String cardType();

    /**
     * 持久化：调业务接口落地
     *
     * @param payload 卡片 payload
     * @param userId  用户 ID
     * @return 业务落地结果（如创建的计划 ID）
     */
    Object persist(Map<String, Object> payload, String userId);

    /**
     * 校验 payload 合法性（用户编辑后调用）
     *
     * @param payload 卡片 payload
     * @return 校验结果
     */
    default ValidationResult validate(Map<String, Object> payload) {
        return ValidationResult.success();
    }

    /**
     * 校验结果
     */
    record ValidationResult(boolean valid, String errorMessage) {
        /** 校验通过。 */
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        /**
         * 校验失败。
         *
         * @param errorMessage 失败原因（面向调用方）
         */
        public static ValidationResult fail(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}
