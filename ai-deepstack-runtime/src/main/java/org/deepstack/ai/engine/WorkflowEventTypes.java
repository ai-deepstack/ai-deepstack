package org.deepstack.ai.engine;

/**
 * 工作流 SSE / 内部流事件类型。
 */
public final class WorkflowEventTypes {

    public static final String NODE_START = "NODE_START";
    public static final String NODE_COMPLETE = "NODE_COMPLETE";
    public static final String WORKFLOW_COMPLETE = "WORKFLOW_COMPLETE";
    public static final String LLM_STREAM = "LLM_STREAM";
    public static final String CARD = "CARD";
    public static final String CARD_STATUS = "CARD_STATUS";

    private WorkflowEventTypes() {
    }
}
