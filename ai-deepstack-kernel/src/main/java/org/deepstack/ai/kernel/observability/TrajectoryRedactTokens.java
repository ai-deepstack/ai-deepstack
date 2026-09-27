package org.deepstack.ai.kernel.observability;

/**
 * 轨迹脱敏遮盖串与节点时间线敏感字段名。
 */
public final class TrajectoryRedactTokens {

    /** 手机号遮盖。 */
    public static final String PHONE = "***PHONE***";
    /** API Key 遮盖。 */
    public static final String API_KEY = "***API_KEY***";
    /** Bearer token 遮盖（含 Bearer 前缀）。 */
    public static final String BEARER = "Bearer ***TOKEN***";
    /** 截断后缀。 */
    public static final String TRUNCATE_SUFFIX = "…(truncated)";

    /** 节点输入字段。 */
    public static final String NODE_INPUT = "input";
    /** 节点输出字段。 */
    public static final String NODE_OUTPUT = "output";
    /** 节点错误字段。 */
    public static final String NODE_ERROR = "error";
    /** 节点错误消息字段。 */
    public static final String NODE_ERROR_MESSAGE = "errorMessage";

    private TrajectoryRedactTokens() {
    }
}
