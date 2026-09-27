package org.deepstack.ai.engine.support;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * 节点执行通用重试工具，指数退避。
 * <p>
 * 对瞬时失败重试（429 限流、5xx 服务端错误、超时、网络错误）。
 * 默认：最多 3 次尝试，初始延迟 1s，倍数 2x，最大延迟 10s。
 * </p>
 *
 */
@Slf4j
public class NodeRetryExecutor {

    /**
     * 默认最大尝试次数（首次调用 + 2 次重试 = 共 3 次）。
     */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * 默认重试初始延迟（毫秒）。
     */
    public static final long DEFAULT_INITIAL_DELAY_MS = 1000L;

    /**
     * 默认退避倍数。
     */
    public static final double DEFAULT_MULTIPLIER = 2.0;

    /**
     * 默认最大重试延迟（毫秒）。
     */
    public static final long DEFAULT_MAX_DELAY_MS = 10_000L;

    private final int maxAttempts;
    private final long initialDelayMs;
    private final double multiplier;
    private final long maxDelayMs;
    private final String nodeDescription;

    private NodeRetryExecutor(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelayMs = builder.initialDelayMs;
        this.multiplier = builder.multiplier;
        this.maxDelayMs = builder.maxDelayMs;
        this.nodeDescription = builder.nodeDescription;
    }

    /**
     * 带重试逻辑执行 callable。
     *
     * @param callable 待执行操作
     * @param <T>      返回类型
     * @return callable 的结果
     * @throws Exception 全部尝试失败时抛出
     */
    public <T> T execute(Callable<T> callable) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callable.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    break;
                }
                long delay = calculateDelay(attempt);
                log.warn("[{}] Attempt {}/{} failed ({}), retrying in {}ms...",
                        nodeDescription, attempt, maxAttempts,
                        e.getClass().getSimpleName() + ": " + e.getMessage(), delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }
        if (lastException != null) {
            log.error("[{}] retries exhausted after {} attempts: {}",
                    nodeDescription, maxAttempts,
                    lastException.getClass().getSimpleName() + ": " + lastException.getMessage(),
                    lastException);
        }
        throw lastException;
    }

    /**
     * 判断异常是否可重试。
     * <p>
     * 可重试条件：
     * <ul>
     *   <li>TimeoutException / SocketTimeoutException</li>
     *   <li>HTTP 429 Too Many Requests（LLM API 常见）</li>
     *   <li>HTTP 5xx 服务端错误</li>
     *   <li>IOException / 网络连通性问题</li>
     *   <li>包装了上述任意类型的 RuntimeException</li>
     * </ul>
     * </p>
     *
     * @param e 待判断异常
     * @return 可重试则为 true
     */
    static boolean isRetryable(Exception e) {
        Throwable current = e;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage() != null ? current.getMessage() : "";

            // 超时
            if (className.contains("TimeoutException")
                    || className.contains("SocketTimeoutException")
                    || className.contains("ConnectTimeoutException")) {
                return true;
            }

            // HTTP 429 限流
            if (message.contains("429") || message.contains("Too Many Requests")
                    || message.contains("rate_limit") || message.contains("rate limit")) {
                return true;
            }

            // HTTP 5xx 服务端错误 —— 匹配独立 5xx token 或 HTTP/status 前缀后的码
            if (message.matches(".*\\b5\\d{2}\\b.*")
                    || message.contains("Server Error")
                    || message.contains("Bad Gateway")
                    || message.contains("Service Unavailable")
                    || message.contains("Gateway Timeout")
                    || message.contains("Internal Server Error")) {
                return true;
            }

            // 网络 / IO 错误 —— 排除 FileNotFoundException（不可重试）
            if (className.contains("ConnectException")
                    || className.contains("SocketException")
                    || className.contains("UnknownHostException")
                    || className.contains("NoRouteToHostException")
                    || (className.contains("IOException") && !className.contains("FileNotFound")
                        && !className.contains("EOFException"))) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }

    /**
     * 按尝试次数计算指数退避延迟（含抖动）。
     */
    private long calculateDelay(int attempt) {
        // 指数退避：initialDelay * multiplier^(attempt-1)
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt - 1));
        delay = Math.min(delay, maxDelayMs);
        // 加入抖动（±25%），避免限流 API 上的惊群
        long jitter = (long) (delay * 0.25 * (Math.random() * 2 - 1));
        return Math.max(0, delay + jitter);
    }

    // ===== 构建器 =====

    /**
     * @return 新的 Builder，使用默认重试参数
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link NodeRetryExecutor} 构建器。
     */
    public static class Builder {
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private long initialDelayMs = DEFAULT_INITIAL_DELAY_MS;
        private double multiplier = DEFAULT_MULTIPLIER;
        private long maxDelayMs = DEFAULT_MAX_DELAY_MS;
        private String nodeDescription = "node";

        /**
         * @param maxAttempts 最大尝试次数（至少 1）
         */
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
            return this;
        }

        /**
         * @param initialDelayMs 首次重试前延迟毫秒（至少 100）
         */
        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = Math.max(100, initialDelayMs);
            return this;
        }

        /**
         * @param multiplier 指数退避倍数（至少 1.0）
         */
        public Builder multiplier(double multiplier) {
            this.multiplier = Math.max(1.0, multiplier);
            return this;
        }

        /**
         * @param maxDelayMs 单次重试最大延迟毫秒（至少 1000）
         */
        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = Math.max(1000, maxDelayMs);
            return this;
        }

        /**
         * @param nodeDescription 日志中的节点描述
         */
        public Builder nodeDescription(String nodeDescription) {
            this.nodeDescription = nodeDescription;
            return this;
        }

        /**
         * @return 构建完成的重试执行器
         */
        public NodeRetryExecutor build() {
            return new NodeRetryExecutor(this);
        }
    }
}
