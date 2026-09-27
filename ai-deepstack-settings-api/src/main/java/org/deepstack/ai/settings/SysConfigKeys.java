package org.deepstack.ai.settings;

/**
 * 运营级 sys_config 键名（与 bootstrap 种子一致）。
 */
public final class SysConfigKeys {

    /** 平台图谱业务总开关（是否类 0/1） */
    public static final String GRAPH_ENABLED = "graph.enabled";
    /** 长期记忆总开关（是否类 0/1） */
    public static final String MEMORY_ENABLED = "memory.enabled";
    /** 未显式传 enableGraph 时，记忆是否默认走图（是否类 0/1） */
    public static final String MEMORY_GRAPH_BY_DEFAULT = "memory.graph-by-default";
    /** MCP 总开关（是否类 0/1） */
    public static final String MCP_ENABLED = "mcp.enabled";
    /** 同步进 ai_tool 的 MCP 工具编码前缀 */
    public static final String MCP_TOOL_CODE_PREFIX = "mcp.tool-code-prefix";
    /** 工具披露模式：off / progressive */
    public static final String TOOLS_DISCLOSURE_MODE = "tools.disclosure-mode";
    /** progressive 下，允许集小于该阈值时仍全量披露 */
    public static final String TOOLS_PROGRESSIVE_FULL_BELOW = "tools.progressive-full-below";
    /** tool_search 返回条数 */
    public static final String TOOLS_SEARCH_TOP_K = "tools.search-top-k";
    /** 图 Checkpoint / HITL 开关（是否类 0/1） */
    public static final String CHECKPOINT_ENABLED = "checkpoint.enabled";
    /** 知识库召回时图谱扩边跳数 */
    public static final String KNOWLEDGE_GRAPH_HOPS = "knowledge.graph.hops";
    /** 建图时每批送模型的 chunk 数 */
    public static final String KNOWLEDGE_GRAPH_MAX_CHUNKS_PER_EXTRACT = "knowledge.graph.max-chunks-per-extract";
    /** 图谱抽取使用的全局默认 CHAT 模型编码 */
    public static final String KNOWLEDGE_GRAPH_CHAT_MODEL_CODE = "knowledge.graph.chat-model-code";
    /** 图扩展追加上限；空表示跟 topK */
    public static final String KNOWLEDGE_GRAPH_EXTRA_TOP_K = "knowledge.graph.graph-extra-top-k";
    /** 多知识库召回并行上限 */
    public static final String KNOWLEDGE_RECALL_MAX_KB_CONCURRENCY = "knowledge.recall.max-kb-concurrency";
    /** 单库多种子扩边并行上限 */
    public static final String KNOWLEDGE_RECALL_MAX_EXPAND_CONCURRENCY = "knowledge.recall.max-expand-concurrency";
    /** 单库召回超时（毫秒） */
    public static final String KNOWLEDGE_RECALL_TIMEOUT_MS = "knowledge.recall.timeout-ms";

    /** 模型 HTTP 连接超时（毫秒） */
    public static final String LLM_CONNECT_TIMEOUT_MS = "llm.connect-timeout-ms";
    /** 模型 HTTP 读超时（毫秒） */
    public static final String LLM_READ_TIMEOUT_MS = "llm.read-timeout-ms";
    /** 长期记忆图扩边并发 */
    public static final String MEMORY_RECALL_MAX_EXPAND_CONCURRENCY = "memory.recall.max-expand-concurrency";
    /** MCP 启动建连并发 */
    public static final String MCP_BOOTSTRAP_MAX_CONCURRENCY = "mcp.bootstrap.max-concurrency";
    /** CHAT 拼 system prompt（画像/RAG/记忆）总超时毫秒 */
    public static final String CHAT_CONTEXT_TIMEOUT_MS = "chat.context.timeout-ms";

    /** 运行轨迹脱敏开关 */
    public static final String RUN_REDACT_ENABLED = "run.redact.enabled";
    /** 用户消息落库最大字符数 */
    public static final String RUN_REDACT_MAX_USER_MESSAGE = "run.redact.max-user-message";
    /** 运行结果落库最大字符数 */
    public static final String RUN_REDACT_MAX_RESULT = "run.redact.max-result";
    /** 节点 input/output/error 落库最大字符数 */
    public static final String RUN_REDACT_MAX_NODE_IO = "run.redact.max-node-io";

    /** 全站并发上限（RUNNING）；0=不限 */
    public static final String QUOTA_GLOBAL_CONCURRENCY = "quota.global.concurrency";
    /** 账号 QPS；0=不限 */
    public static final String QUOTA_ACCOUNT_QPS = "quota.account.qps";
    /** 账号并发；0=不限 */
    public static final String QUOTA_ACCOUNT_CONCURRENCY = "quota.account.concurrency";
    /** 账号每日 token；0=不限 */
    public static final String QUOTA_ACCOUNT_DAILY_TOKENS = "quota.account.daily-tokens";

    /** HITL 全局超时分钟 */
    public static final String HITL_TIMEOUT_MINUTES = "hitl.timeout-minutes";

    /** 告警开关 */
    public static final String ALERT_ENABLED = "alert.enabled";
    /** 告警 Webhook URL */
    public static final String ALERT_WEBHOOK_URL = "alert.webhook-url";
    /** 错误率阈值（如 0.5） */
    public static final String ALERT_ERROR_RATE_THRESHOLD = "alert.error-rate-threshold";
    /** P95 延迟阈值毫秒 */
    public static final String ALERT_P95_MS_THRESHOLD = "alert.p95-ms-threshold";
    /** HITL 积压阈值 */
    public static final String ALERT_HITL_BACKLOG_THRESHOLD = "alert.hitl-backlog-threshold";
    /** 在跑数阈值 */
    public static final String ALERT_RUNNING_THRESHOLD = "alert.running-threshold";
    /** 告警统计窗口分钟 */
    public static final String ALERT_WINDOW_MINUTES = "alert.window-minutes";

    private SysConfigKeys() {
    }
}
