package org.deepstack.ai.runtime.observability;

import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 节点执行记录（写入 WorkflowState.NODE_EXECUTIONS / agent_workflow_run.node_executions）。
 * <p>status 为 {@link GraphRunStatusEnum} 数字码。</p>
 */
public final class NodeExecutionRecords {

    /** 节点 ID。 */
    public static final String KEY_NODE_ID = "nodeId";
    /** 节点显示名。 */
    public static final String KEY_NAME = "name";
    /** 节点类型编码。 */
    public static final String KEY_TYPE = "type";
    /** 输入摘要。 */
    public static final String KEY_INPUT = "input";
    /** 输出摘要。 */
    public static final String KEY_OUTPUT = "output";
    /** 耗时毫秒。 */
    public static final String KEY_DURATION_MS = "durationMs";
    /** 状态码（GraphRunStatusEnum）。 */
    public static final String KEY_STATUS = "status";
    /** 状态中文名。 */
    public static final String KEY_STATUS_NAME = "statusName";
    /** 错误信息。 */
    public static final String KEY_ERROR = "error";

    /** 禁止实例化。 */
    private NodeExecutionRecords() {
    }

    /**
     * 构造成功节点记录。
     *
     * @param nodeId     节点 ID
     * @param name       显示名
     * @param type       类型编码
     * @param input      输入摘要
     * @param output     输出摘要
     * @param durationMs 耗时毫秒
     * @return 记录 map
     */
    public static Map<String, Object> success(String nodeId, String name, String type,
                                              String input, String output, long durationMs) {
        Map<String, Object> record = base(nodeId, name, type, input, durationMs, GraphRunStatusEnum.SUCCESS);
        record.put(KEY_OUTPUT, output);
        return record;
    }

    /**
     * 构造失败节点记录。
     *
     * @param nodeId     节点 ID
     * @param name       显示名
     * @param type       类型编码
     * @param input      输入摘要
     * @param durationMs 耗时毫秒
     * @param error      错误信息
     * @return 记录 map
     */
    public static Map<String, Object> failed(String nodeId, String name, String type,
                                             String input, long durationMs, String error) {
        Map<String, Object> record = base(nodeId, name, type, input, durationMs, GraphRunStatusEnum.FAILED);
        record.put(KEY_ERROR, error != null ? error : "unknown");
        return record;
    }

    /**
     * 公共字段底座。
     *
     * @param nodeId     节点 ID
     * @param name       显示名
     * @param type       类型编码
     * @param input      输入摘要
     * @param durationMs 耗时毫秒
     * @param status     状态枚举
     * @return 基础 map
     */
    private static Map<String, Object> base(String nodeId, String name, String type,
                                            String input, long durationMs, GraphRunStatusEnum status) {
        Objects.requireNonNull(status, "status");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put(KEY_NODE_ID, nodeId);
        record.put(KEY_NAME, name);
        record.put(KEY_TYPE, type);
        record.put(KEY_INPUT, input);
        record.put(KEY_DURATION_MS, durationMs);
        record.put(KEY_STATUS, status.getCode());
        record.put(KEY_STATUS_NAME, status.getLabel());
        return record;
    }
}
