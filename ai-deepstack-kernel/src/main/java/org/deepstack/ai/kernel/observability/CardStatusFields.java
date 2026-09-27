package org.deepstack.ai.kernel.observability;

/**
 * {@code card_status} SSE JSON 字段。
 */
public final class CardStatusFields {

    /** 卡片阶段（见 {@link CardStatusPhases}）。 */
    public static final String PHASE = "phase";
    /** 产出卡片的工具名。 */
    public static final String TOOL_NAME = "toolName";
    /** 状态说明文案。 */
    public static final String MESSAGE = "message";
    /** 节点 ID。 */
    public static final String NODE_ID = "nodeId";
    /** 节点显示名。 */
    public static final String NODE_NAME = "nodeName";

    /** 禁止实例化。 */
    private CardStatusFields() {
    }
}
