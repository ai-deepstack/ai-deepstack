package org.deepstack.ai.knowledge.support;

/**
 * 召回 SQL 的语句超时（秒），仅对当前线程生效。
 * <p>
 * {@link RecallStatementTimeoutInterceptor} 在 {@code Statement.prepare} 之后读取并调用
 * {@code setQueryTimeout}，使 pgvector / 全文在超时后由数据库取消，召回线程能回到池里。
 * </p>
 */
public final class RecallQueryTimeout {

    private static final ThreadLocal<Integer> SECONDS = new ThreadLocal<>();

    private RecallQueryTimeout() {
    }

    /** @param seconds 语句超时秒数；null 或 ≤0 表示本线程不强制 */
    public static void setSeconds(int seconds) {
        if (seconds > 0) {
            SECONDS.set(seconds);
        } else {
            SECONDS.remove();
        }
    }

    /** 返回当前召回语句超时秒数。 */
    public static Integer seconds() {
        return SECONDS.get();
    }

    /** 清除线程内召回超时。 */
    public static void clear() {
        SECONDS.remove();
    }
}
