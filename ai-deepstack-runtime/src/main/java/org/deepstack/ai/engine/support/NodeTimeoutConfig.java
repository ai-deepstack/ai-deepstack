package org.deepstack.ai.engine.support;

import java.util.Map;

/**
 * 从节点属性提取的节点级超时配置。
 * <p>
 * 每个节点可在 properties 中指定 {@code timeoutSeconds}。
 * 未设置时使用工作流 executionConfig 中的默认超时。
 * 两者都未设置时，系统默认 30 秒。
 * </p>
 *
 */
public class NodeTimeoutConfig {

    /**
     * 系统默认超时：30 秒。
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final int timeoutSeconds;

    /**
     * @param timeoutSeconds 超时秒数（正整数）
     */
    public NodeTimeoutConfig(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 从节点属性解析超时，回退到工作流级默认值。
     *
     * @param nodeProperties    节点级属性（可能含 "timeoutSeconds"）
     * @param workflowDefaultMs 工作流级默认超时（毫秒，可为 null）
     * @return 解析后的 NodeTimeoutConfig
     */
    public static NodeTimeoutConfig resolve(Map<String, Object> nodeProperties, Integer workflowDefaultMs) {
        // 级别 1：节点级覆盖
        if (nodeProperties != null && nodeProperties.containsKey("timeoutSeconds")) {
            Object val = nodeProperties.get("timeoutSeconds");
            if (val instanceof Number n && n.intValue() > 0) {
                return new NodeTimeoutConfig(n.intValue());
            }
        }
        // 级别 2：工作流级默认
        if (workflowDefaultMs != null && workflowDefaultMs > 0) {
            return new NodeTimeoutConfig(Math.max(1, workflowDefaultMs / 1000));
        }
        // 级别 3：系统默认
        return new NodeTimeoutConfig(DEFAULT_TIMEOUT_SECONDS);
    }

    /** @return 超时秒数 */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 超时毫秒数，供 CompletableFuture.orTimeout() 使用。
     */
    public long getTimeoutMillis() {
        return (long) timeoutSeconds * 1000;
    }

    /** toString。 */
    @Override
    public String toString() {
        return "NodeTimeoutConfig{timeoutSeconds=" + timeoutSeconds + "}";
    }
}
