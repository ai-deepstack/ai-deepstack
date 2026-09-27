package org.deepstack.ai.kernel.observability;

/**
 * SSE {@code usage} 与运行摘要字段名。
 */
public final class AgentRunUsageKeys {

    /** 端到端延迟毫秒。 */
    public static final String LATENCY_MS = "latencyMs";
    /** 首 token 延迟毫秒。 */
    public static final String FIRST_TOKEN_LATENCY_MS = "firstTokenLatencyMs";
    /** prompt tokens。 */
    public static final String PROMPT_TOKENS = "promptTokens";
    /** completion tokens。 */
    public static final String COMPLETION_TOKENS = "completionTokens";
    /** total tokens。 */
    public static final String TOTAL_TOKENS = "totalTokens";
    /** agent_workflow_run.id。 */
    public static final String RUN_ID = "runId";
    /** Micrometer traceId。 */
    public static final String TRACE_ID = "traceId";
    /** TraceMode 枚举名。 */
    public static final String TRACE_MODE = "traceMode";

    /** 禁止实例化。 */
    private AgentRunUsageKeys() {
    }
}
