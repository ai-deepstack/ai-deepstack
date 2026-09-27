package org.deepstack.ai.engine;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 图执行请求：编排侧加工后的注入数据（运行侧不再回查 Agent 绑定）。
 */
@Data
public class GraphRunRequest implements Serializable {

    private String message;

    private String userId;

    private String conversationId;

    /** 智能体编码（长期记忆隔离等） */
    private String agentCode;

    /**
     * Checkpoint 线程 ID；为空时执行侧自动生成（如 run-xxx）。
     * HITL resume 时必须带回同一 threadId。
     */
    private String threadId;

    /**
     * true = 从 checkpoint 恢复，不重新注入初始 messages。
     */
    private boolean resume;

    /**
     * resume 时合并进状态的增量（card_action、modifiedPayload 等）。
     */
    private Map<String, Object> resumeUpdates;

    /**
     * 已解析的工具编码列表（编排从绑定表加工后注入）。
     */
    private List<String> toolCodes;

    /**
     * 对话历史（可选），每条含 role / content。
     */
    private List<Map<String, String>> chatHistory;
}
