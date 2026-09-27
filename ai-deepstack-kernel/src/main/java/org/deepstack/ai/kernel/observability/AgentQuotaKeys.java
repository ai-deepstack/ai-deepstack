package org.deepstack.ai.kernel.observability;

/**
 * 配额 Redis / 进程内计数键前缀与组装。
 * <p>
 * 智能体编码统一 trim，避免 peek / hold / release 键不一致。
 * </p>
 */
public final class AgentQuotaKeys {

    /** 全站并发占用键。 */
    public static final String GLOBAL_CONCURRENCY = "quota:global:conc";

    private static final String AGENT_QPS = "quota:agent:qps:";
    private static final String AGENT_CONCURRENCY = "quota:agent:conc:";
    private static final String AGENT_TOKENS = "quota:agent:tokens:";
    private static final String ACCOUNT_QPS = "quota:account:qps:";
    private static final String ACCOUNT_CONCURRENCY = "quota:account:conc:";
    private static final String ACCOUNT_TOKENS = "quota:account:tokens:";

    private AgentQuotaKeys() {
    }

    /**
     * 规范化智能体编码（trim）；空白返回 null。
     *
     * @param agentCode 原始编码
     * @return 规范化编码或 null
     */
    public static String normalizeAgentCode(String agentCode) {
        if (agentCode == null) {
            return null;
        }
        String t = agentCode.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 智能体 QPS 键。
     *
     * @param agentCode 智能体编码（会规范化）
     * @return 键；编码无效时返回 null
     */
    public static String agentQps(String agentCode) {
        String code = normalizeAgentCode(agentCode);
        return code == null ? null : AGENT_QPS + code;
    }

    /**
     * 智能体并发键。
     *
     * @param agentCode 智能体编码（会规范化）
     * @return 键；编码无效时返回 null
     */
    public static String agentConcurrency(String agentCode) {
        String code = normalizeAgentCode(agentCode);
        return code == null ? null : AGENT_CONCURRENCY + code;
    }

    /**
     * 智能体日 token 键。
     *
     * @param agentCode 智能体编码（会规范化）
     * @param date      日期字符串，如 2026-09-21
     * @return 键；编码无效时返回 null
     */
    public static String agentDailyTokens(String agentCode, String date) {
        String code = normalizeAgentCode(agentCode);
        return code == null ? null : AGENT_TOKENS + code + ":" + date;
    }

    /**
     * 账号 QPS 键。
     *
     * @param loginUserId 登录用户 id
     * @return 键
     */
    public static String accountQps(Long loginUserId) {
        return ACCOUNT_QPS + loginUserId;
    }

    /**
     * 账号并发键。
     *
     * @param loginUserId 登录用户 id
     * @return 键
     */
    public static String accountConcurrency(Long loginUserId) {
        return ACCOUNT_CONCURRENCY + loginUserId;
    }

    /**
     * 账号日 token 键。
     *
     * @param loginUserId 登录用户 id
     * @param date        日期字符串
     * @return 键
     */
    public static String accountDailyTokens(Long loginUserId, String date) {
        return ACCOUNT_TOKENS + loginUserId + ":" + date;
    }
}
