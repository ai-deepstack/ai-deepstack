package org.deepstack.ai.engine;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 图执行结果（编排试跑 / Chat GRAPH 共用契约）。
 */
@Data
public class GraphRunResponse implements Serializable {

    /** 执行记录 ID（试跑可取消时返回） */
    private Long runId;

    /** 状态码：见 {@link org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum} */
    private Integer status;

    /** 状态中文名 */
    private String statusName;

    private String result;

    /** Checkpoint 线程 ID（HITL 恢复用） */
    private String threadId;

    /** 挂起时下一节点（若可得） */
    private String nextNodeId;

    /** [{nodeId, name, input, output, durationMs, status}] */
    private List<Map<String, Object>> nodeExecutions;

    private Integer totalTokens;

    private Integer totalDurationMs;

    private String errorMessage;

    /** 设置状态码并同步中文名。 */
    public void applyStatus(org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum status) {
        if (status == null) {
            this.status = null;
            this.statusName = null;
            return;
        }
        this.status = status.getCode();
        this.statusName = status.getLabel();
    }
}
