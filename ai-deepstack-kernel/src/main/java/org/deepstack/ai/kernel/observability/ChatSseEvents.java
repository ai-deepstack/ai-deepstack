package org.deepstack.ai.kernel.observability;

/**
 * 对话 / GRAPH SSE 对外事件名（与前端约定一致）。
 */
public final class ChatSseEvents {

    /** 会话 ID 推送。 */
    public static final String CONVERSATION = "conversation";
    /** LLM 文本 chunk。 */
    public static final String MESSAGE = "message";
    /** 交互卡片 JSON。 */
    public static final String CARD = "card";
    /** 卡片生命周期状态。 */
    public static final String CARD_STATUS = "card_status";
    /** 卡片动作结果。 */
    public static final String CARD_ACTION_RESULT = "card_action_result";
    /** 节点开始。 */
    public static final String AGENT_NODE_START = "agent_node_start";
    /** 节点完成。 */
    public static final String AGENT_NODE_COMPLETE = "agent_node_complete";
    /** 工作流结束。 */
    public static final String WORKFLOW_COMPLETE = "workflow_complete";
    /** 未分类图事件透传。 */
    public static final String AGENT_EVENT = "agent_event";
    /** 编排侧错误。 */
    public static final String AGENT_ERROR = "agent_error";
    /** usage / 延迟 / runId。 */
    public static final String USAGE = "usage";
    /** 流结束标记。 */
    public static final String DONE = "done";
    /** 通用错误事件。 */
    public static final String ERROR = "error";

    /** 禁止实例化。 */
    private ChatSseEvents() {
    }
}
