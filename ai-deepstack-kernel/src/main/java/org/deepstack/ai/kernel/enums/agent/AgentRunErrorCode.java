package org.deepstack.ai.kernel.enums.agent;

/**
 * 智能体运行错误分类（写入 agent_workflow_run.error_code，供概览聚合）。
 */
public enum AgentRunErrorCode {

    NONE("NONE", "无"),
    MODEL_TIMEOUT("MODEL_TIMEOUT", "模型超时"),
    MODEL_ERROR("MODEL_ERROR", "模型调用失败"),
    MCP_ERROR("MCP_ERROR", "MCP/工具失败"),
    KNOWLEDGE_TIMEOUT("KNOWLEDGE_TIMEOUT", "知识库超时"),
    KNOWLEDGE_ERROR("KNOWLEDGE_ERROR", "知识库失败"),
    VALIDATION("VALIDATION", "参数校验失败"),
    USER_CANCELLED("USER_CANCELLED", "用户取消"),
    HITL_TIMEOUT("HITL_TIMEOUT", "人工确认超时"),
    HITL_REJECTED("HITL_REJECTED", "人工拒绝"),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "配额超限"),
    SYSTEM("SYSTEM", "系统异常");

    private final String code;
    private final String label;

    AgentRunErrorCode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 返回 Code。 */
    public String getCode() {
        return code;
    }

    /** 返回 Label。 */
    public String getLabel() {
        return label;
    }

    /** 按数字码或原始值解析，无法识别时返回 null。 */
    public static AgentRunErrorCode of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (AgentRunErrorCode e : values()) {
            if (e.code.equalsIgnoreCase(raw.trim())) {
                return e;
            }
        }
        return null;
    }

    /**
     * 从异常消息粗分错误类（无法判定时 {@link #SYSTEM}）。
     */
    public static AgentRunErrorCode classify(Throwable t) {
        if (t == null) {
            return SYSTEM;
        }
        String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
        String name = t.getClass().getSimpleName().toLowerCase();
        if (name.contains("timeout") || msg.contains("timeout") || msg.contains("timed out")
                || msg.contains("超时")) {
            if (msg.contains("embed") || msg.contains("knowledge") || msg.contains("召回")
                    || msg.contains("rag")) {
                return KNOWLEDGE_TIMEOUT;
            }
            return MODEL_TIMEOUT;
        }
        if (msg.contains("mcp") || msg.contains("tool") || msg.contains("工具")) {
            return MCP_ERROR;
        }
        if (msg.contains("knowledge") || msg.contains("知识库") || msg.contains("embedding")) {
            return KNOWLEDGE_ERROR;
        }
        if (msg.contains("invalid") || msg.contains("不能为空") || msg.contains("校验")
                || name.contains("illegalargument")) {
            return VALIDATION;
        }
        if (msg.contains("cancel") || msg.contains("取消")) {
            return USER_CANCELLED;
        }
        if (msg.contains("model") || msg.contains("openai") || msg.contains("llm")
                || msg.contains("chat")) {
            return MODEL_ERROR;
        }
        return SYSTEM;
    }
}
