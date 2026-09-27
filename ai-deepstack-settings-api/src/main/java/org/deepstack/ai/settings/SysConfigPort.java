package org.deepstack.ai.settings;

/**
 * 系统配置只读 SPI：能力模块消费此端口，不依赖 DB/Redis/管理 API。
 * <p>
 * App 提供 {@code SysConfigPortImpl}（Redis→DB→yml→coded）；
 * 无 App 实现时由 yml 回落 Bean 提供。
 * </p>
 */
public interface SysConfigPort {

    /** 原始字符串；miss 时回落 yml/默认。 */
    String getString(String key);

    /**
     * 整数。Redis→DB→yml 都没有或无法解析时，用代码默认（{@code SysConfigFallback.CODED_DEFAULTS}），再没有则为 0。
     */
    int getInt(String key);

    /** 整数；无法解析时用 defaultValue（调用方自备兜底，优先用 {@link #getInt(String)}）。 */
    int getInt(String key, int defaultValue);

    /**
     * 浮点。解析失败时用代码默认，再没有则为 0。
     */
    double getDouble(String key);

    /**
     * 是否类配置为「是」。
     * <p>库内/缓存为 {@code "0"}/{@code "1"}；无法解析时按否。
     */
    boolean isYes(String key);
}
