package org.deepstack.ai.engine;

import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流流式事件。状态一律用 {@link #statusCode}（{@link GraphRunStatusEnum}），卡片阶段用 {@link #phase}。
 */
@Data
public class WorkflowStreamEvent implements Serializable {

    /** 见 {@link WorkflowEventTypes} */
    private String eventType;

    private String nodeId;
    private String nodeName;
    private String nodeType;

    /** NODE_START 输入摘要；CARD_STATUS 时为 toolName */
    private String input;

    private Long durationMs;

    /** 节点 / 整图状态码（GraphRunStatusEnum） */
    private Integer statusCode;

    /** 状态中文名 */
    private String statusName;

    /** CARD_STATUS 阶段：generating / failed */
    private String phase;

    private String errorMessage;
    private String result;
    private List<Map<String, Object>> nodeExecutions;

    public WorkflowStreamEvent() {
    }

    /** 构造节点开始流事件。 */
    public static WorkflowStreamEvent nodeStart(String nodeId, String nodeName, String nodeType, String input) {
        WorkflowStreamEvent event = new WorkflowStreamEvent();
        event.setEventType(WorkflowEventTypes.NODE_START);
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setNodeType(nodeType);
        event.setInput(input != null && input.length() > 500 ? input.substring(0, 500) + "..." : input);
        return event;
    }

    /** 构造节点完成流事件。 */
    public static WorkflowStreamEvent nodeComplete(String nodeId, String nodeName, String nodeType,
                                                   long durationMs, GraphRunStatusEnum status,
                                                   String errorMessage) {
        Objects.requireNonNull(status, "status");
        WorkflowStreamEvent event = new WorkflowStreamEvent();
        event.setEventType(WorkflowEventTypes.NODE_COMPLETE);
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setNodeType(nodeType);
        event.setDurationMs(durationMs);
        event.setStatusCode(status.getCode());
        event.setStatusName(status.getLabel());
        event.setErrorMessage(errorMessage);
        return event;
    }

    /** 构造工作流结束流事件。 */
    public static WorkflowStreamEvent workflowComplete(GraphRunStatusEnum status, String result,
                                                       Integer totalDurationMs,
                                                       List<Map<String, Object>> nodeExecutions) {
        return workflowComplete(status, result, totalDurationMs, nodeExecutions, null);
    }

    /** 构造工作流结束流事件。 */
    public static WorkflowStreamEvent workflowComplete(GraphRunStatusEnum status, String result,
                                                        Integer totalDurationMs,
                                                        List<Map<String, Object>> nodeExecutions,
                                                        String errorMessage) {
        Objects.requireNonNull(status, "status");
        WorkflowStreamEvent event = new WorkflowStreamEvent();
        event.setEventType(WorkflowEventTypes.WORKFLOW_COMPLETE);
        event.setStatusCode(status.getCode());
        event.setStatusName(status.getLabel());
        event.setResult(result);
        event.setDurationMs(totalDurationMs != null ? totalDurationMs.longValue() : null);
        event.setNodeExecutions(nodeExecutions);
        event.setErrorMessage(errorMessage);
        return event;
    }

    /** 构造 LLM token 流事件。 */
    public static WorkflowStreamEvent llmStream(String nodeId, String nodeName, String chunk) {
        WorkflowStreamEvent event = new WorkflowStreamEvent();
        event.setEventType(WorkflowEventTypes.LLM_STREAM);
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setResult(chunk);
        return event;
    }

    /** 构造卡片流事件。 */
    public static WorkflowStreamEvent card(String nodeId, String nodeName, String cardJson) {
        WorkflowStreamEvent event = new WorkflowStreamEvent();
        event.setEventType(WorkflowEventTypes.CARD);
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setResult(cardJson);
        return event;
    }

    /** 构造卡片状态流事件。 */
    public static WorkflowStreamEvent cardStatus(String nodeId, String nodeName,
                                                 String phase, String toolName, String message) {
        WorkflowStreamEvent event = new WorkflowStreamEvent();
        event.setEventType(WorkflowEventTypes.CARD_STATUS);
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setPhase(phase);
        event.setInput(toolName);
        event.setResult(message != null ? message : "");
        return event;
    }
}
