package org.deepstack.ai.runtime;

import org.deepstack.ai.engine.api.WorkflowState;
import com.alibaba.fastjson2.JSON;

import java.util.List;
import java.util.Map;

/**
 * 状态 / 输出摘要工具：为节点日志生成轻量快照。
 * <p>
 * 纯函数、无状态，供节点跟踪包装生成 input/output 日志；摘要只截断、不抛异常，
 * 避免日志本身影响执行路径。
 * </p>
 */
public final class WorkflowStateSummarizer {

    private WorkflowStateSummarizer() {
    }

    /**
     * 生成当前状态的轻量快照，用于节点输入日志。
     */
    public static String summarizeState(WorkflowState state) {
        try {
            String intent = state.value(WorkflowState.INTENT).map(Object::toString).orElse("");
            String response = state.value(WorkflowState.RESPONSE).map(Object::toString).orElse("");
            Object messages = state.value(WorkflowState.MESSAGES).orElse(List.of());
            String msgSummary = messages instanceof List<?> list ? "messages[" + list.size() + "]" : "messages[0]";
            return msgSummary + " intent=" + intent + " response=" + (response.length() > 200 ? response.substring(0, 200) + "..." : response);
        } catch (Exception e) {
            return "<state-summary-error>";
        }
    }

    /**
     * 摘要节点输出 map，用于日志。
     */
    public static String summarizeOutput(Map<String, Object> output) {
        if (output == null || output.isEmpty()) return "";
        try {
            String json = JSON.toJSONString(output);
            return json.length() > 500 ? json.substring(0, 500) + "..." : json;
        } catch (Exception e) {
            return output.toString();
        }
    }
}
