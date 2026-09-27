package org.deepstack.ai.app.settings;

import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.settings.SysConfigKeys;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * yml / 代码默认与是否类规范化，供 {@link YmlSysConfigPort} 与 {@link SysConfigPortImpl} 共用。
 */
public final class SysConfigFallback {

    public static final String VALUE_TYPE_YES_NO = "yes_no";
    public static final String VALUE_TYPE_INT = "int";
    public static final String VALUE_TYPE_STRING = "string";
    public static final String VALUE_TYPE_MODEL = "model";

    /** 已知运营 key → yml / 环境变量属性路径（与 application.yml 对齐）。 */
    public static final Map<String, String> YML_PATHS = Map.ofEntries(
            Map.entry(SysConfigKeys.GRAPH_ENABLED, "deepstack.graph.enabled"),
            Map.entry(SysConfigKeys.MEMORY_ENABLED, "deepstack.memory.enabled"),
            Map.entry(SysConfigKeys.MEMORY_GRAPH_BY_DEFAULT, "deepstack.memory.graph-by-default"),
            Map.entry(SysConfigKeys.MCP_ENABLED, "deepstack.mcp.enabled"),
            Map.entry(SysConfigKeys.MCP_TOOL_CODE_PREFIX, "deepstack.mcp.tool-code-prefix"),
            Map.entry(SysConfigKeys.TOOLS_DISCLOSURE_MODE, "deepstack.tools.disclosure-mode"),
            Map.entry(SysConfigKeys.TOOLS_PROGRESSIVE_FULL_BELOW, "deepstack.tools.progressive-full-below"),
            Map.entry(SysConfigKeys.TOOLS_SEARCH_TOP_K, "deepstack.tools.search-top-k"),
            Map.entry(SysConfigKeys.CHECKPOINT_ENABLED, "deepstack.checkpoint.enabled"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_HOPS, "deepstack.knowledge.graph.hops"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_MAX_CHUNKS_PER_EXTRACT, "deepstack.knowledge.graph.max-chunks-per-extract"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_CHAT_MODEL_CODE, "deepstack.knowledge.graph.chat-model-code"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_EXTRA_TOP_K, "deepstack.knowledge.graph.graph-extra-top-k"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_KB_CONCURRENCY, "deepstack.knowledge.recall.max-kb-concurrency"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_EXPAND_CONCURRENCY, "deepstack.knowledge.recall.max-expand-concurrency"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, "deepstack.knowledge.recall.timeout-ms"),
            Map.entry(SysConfigKeys.LLM_CONNECT_TIMEOUT_MS, "deepstack.llm.connect-timeout-ms"),
            Map.entry(SysConfigKeys.LLM_READ_TIMEOUT_MS, "deepstack.llm.read-timeout-ms"),
            Map.entry(SysConfigKeys.MEMORY_RECALL_MAX_EXPAND_CONCURRENCY, "deepstack.memory.recall.max-expand-concurrency"),
            Map.entry(SysConfigKeys.MCP_BOOTSTRAP_MAX_CONCURRENCY, "deepstack.mcp.bootstrap.max-concurrency"),
            Map.entry(SysConfigKeys.CHAT_CONTEXT_TIMEOUT_MS, "deepstack.chat.context.timeout-ms"),
            Map.entry(SysConfigKeys.RUN_REDACT_ENABLED, "deepstack.run.redact.enabled"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_USER_MESSAGE, "deepstack.run.redact.max-user-message"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_RESULT, "deepstack.run.redact.max-result"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_NODE_IO, "deepstack.run.redact.max-node-io"),
            Map.entry(SysConfigKeys.QUOTA_GLOBAL_CONCURRENCY, "deepstack.quota.global.concurrency"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_QPS, "deepstack.quota.account.qps"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_CONCURRENCY, "deepstack.quota.account.concurrency"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_DAILY_TOKENS, "deepstack.quota.account.daily-tokens"),
            Map.entry(SysConfigKeys.HITL_TIMEOUT_MINUTES, "deepstack.hitl.timeout-minutes"),
            Map.entry(SysConfigKeys.ALERT_ENABLED, "deepstack.alert.enabled"),
            Map.entry(SysConfigKeys.ALERT_WEBHOOK_URL, "deepstack.alert.webhook-url"),
            Map.entry(SysConfigKeys.ALERT_ERROR_RATE_THRESHOLD, "deepstack.alert.error-rate-threshold"),
            Map.entry(SysConfigKeys.ALERT_P95_MS_THRESHOLD, "deepstack.alert.p95-ms-threshold"),
            Map.entry(SysConfigKeys.ALERT_HITL_BACKLOG_THRESHOLD, "deepstack.alert.hitl-backlog-threshold"),
            Map.entry(SysConfigKeys.ALERT_RUNNING_THRESHOLD, "deepstack.alert.running-threshold"),
            Map.entry(SysConfigKeys.ALERT_WINDOW_MINUTES, "deepstack.alert.window-minutes")
    );

    public static final Map<String, String> CODED_DEFAULTS = Map.ofEntries(
            Map.entry(SysConfigKeys.GRAPH_ENABLED, "0"),
            Map.entry(SysConfigKeys.MEMORY_ENABLED, "0"),
            Map.entry(SysConfigKeys.MEMORY_GRAPH_BY_DEFAULT, "0"),
            Map.entry(SysConfigKeys.MCP_ENABLED, "1"),
            Map.entry(SysConfigKeys.MCP_TOOL_CODE_PREFIX, "mcp_"),
            Map.entry(SysConfigKeys.TOOLS_DISCLOSURE_MODE, "off"),
            Map.entry(SysConfigKeys.TOOLS_PROGRESSIVE_FULL_BELOW, "5"),
            Map.entry(SysConfigKeys.TOOLS_SEARCH_TOP_K, "8"),
            Map.entry(SysConfigKeys.CHECKPOINT_ENABLED, "1"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_HOPS, "2"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_MAX_CHUNKS_PER_EXTRACT, "8"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_CHAT_MODEL_CODE, ""),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_EXTRA_TOP_K, ""),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_KB_CONCURRENCY, "4"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_EXPAND_CONCURRENCY, "8"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, "3000"),
            Map.entry(SysConfigKeys.LLM_CONNECT_TIMEOUT_MS, "5000"),
            Map.entry(SysConfigKeys.LLM_READ_TIMEOUT_MS, "60000"),
            Map.entry(SysConfigKeys.MEMORY_RECALL_MAX_EXPAND_CONCURRENCY, "4"),
            Map.entry(SysConfigKeys.MCP_BOOTSTRAP_MAX_CONCURRENCY, "4"),
            Map.entry(SysConfigKeys.CHAT_CONTEXT_TIMEOUT_MS, "10000"),
            Map.entry(SysConfigKeys.RUN_REDACT_ENABLED, "1"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_USER_MESSAGE, "2000"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_RESULT, "4000"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_NODE_IO, "1000"),
            Map.entry(SysConfigKeys.QUOTA_GLOBAL_CONCURRENCY, "50"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_QPS, "10"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_CONCURRENCY, "5"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_DAILY_TOKENS, "0"),
            Map.entry(SysConfigKeys.HITL_TIMEOUT_MINUTES, "60"),
            Map.entry(SysConfigKeys.ALERT_ENABLED, "1"),
            Map.entry(SysConfigKeys.ALERT_WEBHOOK_URL, ""),
            Map.entry(SysConfigKeys.ALERT_ERROR_RATE_THRESHOLD, "0.5"),
            Map.entry(SysConfigKeys.ALERT_P95_MS_THRESHOLD, "30000"),
            Map.entry(SysConfigKeys.ALERT_HITL_BACKLOG_THRESHOLD, "20"),
            Map.entry(SysConfigKeys.ALERT_RUNNING_THRESHOLD, "80"),
            Map.entry(SysConfigKeys.ALERT_WINDOW_MINUTES, "15")
    );

    public static final Map<String, String> VALUE_TYPES = Map.ofEntries(
            Map.entry(SysConfigKeys.GRAPH_ENABLED, VALUE_TYPE_YES_NO),
            Map.entry(SysConfigKeys.MEMORY_ENABLED, VALUE_TYPE_YES_NO),
            Map.entry(SysConfigKeys.MEMORY_GRAPH_BY_DEFAULT, VALUE_TYPE_YES_NO),
            Map.entry(SysConfigKeys.MCP_ENABLED, VALUE_TYPE_YES_NO),
            Map.entry(SysConfigKeys.MCP_TOOL_CODE_PREFIX, VALUE_TYPE_STRING),
            Map.entry(SysConfigKeys.TOOLS_DISCLOSURE_MODE, VALUE_TYPE_STRING),
            Map.entry(SysConfigKeys.TOOLS_PROGRESSIVE_FULL_BELOW, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.TOOLS_SEARCH_TOP_K, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.CHECKPOINT_ENABLED, VALUE_TYPE_YES_NO),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_HOPS, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_MAX_CHUNKS_PER_EXTRACT, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_CHAT_MODEL_CODE, VALUE_TYPE_MODEL),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_EXTRA_TOP_K, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_KB_CONCURRENCY, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_EXPAND_CONCURRENCY, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.LLM_CONNECT_TIMEOUT_MS, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.LLM_READ_TIMEOUT_MS, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.MEMORY_RECALL_MAX_EXPAND_CONCURRENCY, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.MCP_BOOTSTRAP_MAX_CONCURRENCY, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.CHAT_CONTEXT_TIMEOUT_MS, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.RUN_REDACT_ENABLED, VALUE_TYPE_YES_NO),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_USER_MESSAGE, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_RESULT, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_NODE_IO, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.QUOTA_GLOBAL_CONCURRENCY, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_QPS, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_CONCURRENCY, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_DAILY_TOKENS, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.HITL_TIMEOUT_MINUTES, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.ALERT_ENABLED, VALUE_TYPE_YES_NO),
            Map.entry(SysConfigKeys.ALERT_WEBHOOK_URL, VALUE_TYPE_STRING),
            Map.entry(SysConfigKeys.ALERT_ERROR_RATE_THRESHOLD, VALUE_TYPE_STRING),
            Map.entry(SysConfigKeys.ALERT_P95_MS_THRESHOLD, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.ALERT_HITL_BACKLOG_THRESHOLD, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.ALERT_RUNNING_THRESHOLD, VALUE_TYPE_INT),
            Map.entry(SysConfigKeys.ALERT_WINDOW_MINUTES, VALUE_TYPE_INT)
    );

    private SysConfigFallback() {
    }

    /**
     * 解析 yml/环境变量 → 代码默认；是否类规范化为 0/1。不写 Redis。
     *
     * @return 有效值；未知 key 且无默认时可能为 null
     */
    public static String resolveFallback(Environment environment, String key) {
        String ymlPath = YML_PATHS.get(key);
        if (ymlPath != null && environment != null) {
            String fromEnv = environment.getProperty(ymlPath);
            if (fromEnv != null) {
                if (VALUE_TYPE_YES_NO.equals(VALUE_TYPES.get(key))) {
                    return normalizeYesNoStored(fromEnv);
                }
                return fromEnv;
            }
        }
        String coded = CODED_DEFAULTS.get(key);
        if (coded != null && VALUE_TYPE_YES_NO.equals(VALUE_TYPES.get(key))) {
            return normalizeYesNoStored(coded);
        }
        return coded;
    }

    /** 是否存在对应 yml/环境变量属性（用于 list 的 source 判定）。 */
    public static boolean hasYmlProperty(Environment environment, String key) {
        String ymlPath = YML_PATHS.get(key);
        if (ymlPath == null || environment == null) {
            return false;
        }
        return environment.getProperty(ymlPath) != null;
    }

    /** 入库/缓存只认 0/1；兼容 true/false 迁移兜底。空串视为否。 */
    public static String normalizeYesNoStored(String raw) {
        if (raw == null) {
            return "0";
        }
        String t = raw.trim().toLowerCase();
        if ("1".equals(t) || "true".equals(t) || "yes".equals(t)) {
            return "1";
        }
        if ("0".equals(t) || "false".equals(t) || "no".equals(t) || t.isEmpty()) {
            return "0";
        }
        return "0";
    }

    /** 把原始值解析为 0/1。 */
    public static Integer parseYesNoCode(String raw) {
        String n = normalizeYesNoStored(raw);
        return "1".equals(n) ? YesNo.YES.getCode() : YesNo.NO.getCode();
    }

    /** 是否 YesRaw。 */
    public static boolean isYesRaw(String raw) {
        return "1".equals(normalizeYesNoStored(raw));
    }

    /** 解析整数，失败时返回默认值。 */
    public static int parseIntOrDefault(String raw, int defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 解析浮点，失败时返回默认值。 */
    public static double parseDoubleOrDefault(String raw, double defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 已解析出的原始值优先；空或非法时回落 {@link #CODED_DEFAULTS}。
     *
     * @param raw 端口已取出的字符串（含 yml/代码默认）
     * @param key 配置键
     * @return 整数值
     */
    public static int resolveInt(String raw, String key) {
        return parseIntOrDefault(raw, parseIntOrDefault(CODED_DEFAULTS.get(key), 0));
    }

    /**
     * 已解析出的原始值优先；空或非法时回落 {@link #CODED_DEFAULTS}。
     *
     * @param raw 端口已取出的字符串
     * @param key 配置键
     * @return 浮点值
     */
    public static double resolveDouble(String raw, String key) {
        return parseDoubleOrDefault(raw, parseDoubleOrDefault(CODED_DEFAULTS.get(key), 0D));
    }
}
