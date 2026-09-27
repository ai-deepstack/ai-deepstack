package org.deepstack.ai.kernel.observability;

/**
 * 智能体运行相关 Micrometer 指标名与标签键。
 */
public final class AgentRunMetricNames {

    /** 运行次数计数器。 */
    public static final String RUN_COUNT = "agent.run.count";
    /** 运行耗时计时器。 */
    public static final String RUN_DURATION = "agent.run.duration";
    /** 运行错误计数器。 */
    public static final String RUN_ERROR = "agent.run.error";
    /** token 累加计数器。 */
    public static final String TOKENS = "agent.tokens";
    /** HITL 等待计时器。 */
    public static final String HITL_WAIT = "agent.hitl.wait";
    /** 节点执行耗时计时器。 */
    public static final String NODE_DURATION = "agent.node.duration";

    /** 标签：智能体编码。 */
    public static final String TAG_AGENT = "agent";
    /** 标签：编排模式。 */
    public static final String TAG_MODE = "mode";
    /** 标签：运行状态。 */
    public static final String TAG_STATUS = "status";
    /** 标签：错误码。 */
    public static final String TAG_ERROR = "error";
    /** 标签：节点类型。 */
    public static final String TAG_TYPE = "type";

    /** 空标签兜底值。 */
    public static final String UNKNOWN = "unknown";

    /** 禁止实例化。 */
    private AgentRunMetricNames() {
    }
}
