package org.deepstack.ai.kernel.observability;

/**
 * 运行取消原因（写入 agent_workflow_run.error_message）。
 */
public final class AgentRunCancelReasons {

    /** 用户主动停止。 */
    public static final String USER = "Cancelled by user";
    /** 客户端断开 SSE。 */
    public static final String CLIENT_DISCONNECT = "Client disconnected";

    /** 禁止实例化。 */
    private AgentRunCancelReasons() {
    }
}
