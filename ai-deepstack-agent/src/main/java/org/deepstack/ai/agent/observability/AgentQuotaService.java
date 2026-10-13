package org.deepstack.ai.agent.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.agent.mapper.AgentWorkflowRunMapper;
import org.deepstack.ai.agent.model.entity.AgentWorkflowRun;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.kernel.enums.agent.AgentRunErrorCode;
import org.deepstack.ai.kernel.enums.agent.GraphRunStatusEnum;
import org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.observability.AgentQuotaKeys;
import org.deepstack.ai.runtime.spi.GraphRuntime;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 运行配额：全站并发 → Agent QPS/并发/日 token → 账号 QPS/并发/日 token。
 * <p>
 * Redis 可用时用计数器；否则进程内 {@link ConcurrentHashMap} 兜底（带 TTL 清理）。
 * 键名统一见 {@link AgentQuotaKeys}。
 * </p>
 */
@Slf4j
@Service
public class AgentQuotaService {

    private static final int DEFAULT_GLOBAL_CONCURRENCY = 50;
    private static final int DEFAULT_ACCOUNT_QPS = 10;
    private static final int DEFAULT_ACCOUNT_CONCURRENCY = 5;
    private static final long QPS_WINDOW_MS = 1_000L;
    private static final long DAILY_TOKEN_TTL_HOURS = 48L;
    private static final long CONCURRENCY_TTL_HOURS = 1L;
    private static final long MEMORY_ENTRY_TTL_MS = 2_000L;

    private final AgentWorkflowRunMapper runMapper;
    private final SysConfigPort sysConfigPort;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<GraphRuntime> graphRuntimeProvider;
    private final AgentRunService agentRunService;

    /** 进程内 QPS 滑动窗口计数：key → (窗口起点毫秒, 计数)。 */
    private final ConcurrentHashMap<String, long[]> memoryQps = new ConcurrentHashMap<>();
    /** 进程内并发占用：key → 计数。 */
    private final ConcurrentHashMap<String, AtomicInteger> memoryConcurrency = new ConcurrentHashMap<>();
    /** 进程内日 token：key → (过期毫秒, 累计)。 */
    private final ConcurrentHashMap<String, long[]> memoryDailyTokens = new ConcurrentHashMap<>();

    /**
     * 构造配额服务。
     *
     * @param runMapper                 运行表 Mapper
     * @param sysConfigPort             系统配置
     * @param redisProvider             Redis（可选）
     * @param graphRuntimeProvider      图执行运行时（可选，用于读在跑数）
     * @param agentRunService           运行落库（拒绝行）；Lazy 避免与其它 Bean 循环
     */
    public AgentQuotaService(AgentWorkflowRunMapper runMapper,
                             SysConfigPort sysConfigPort,
                             ObjectProvider<StringRedisTemplate> redisProvider,
                             ObjectProvider<GraphRuntime> graphRuntimeProvider,
                             @Lazy AgentRunService agentRunService) {
        this.runMapper = runMapper;
        this.sysConfigPort = sysConfigPort;
        this.redisProvider = redisProvider;
        this.graphRuntimeProvider = graphRuntimeProvider;
        this.agentRunService = agentRunService;
    }

    /**
     * 启动前占用配额；超限抛 {@link CommonErrorCode#TOO_MANY_REQUESTS}。
     *
     * @param loginUserId 登录用户 AiUser.id，可为 null（跳过账号层）
     * @param agent       Agent 配置，不可为 null
     */
    public void acquire(Long loginUserId, AiAgent agent) {
        if (agent == null) {
            log.warn("配额 acquire 跳过: agent 为空");
            return;
        }
        String agentCode = AgentQuotaKeys.normalizeAgentCode(agent.getAgentCode());
        log.info("配额 acquire 开始: agentCode={}, loginUserId={}", agentCode, loginUserId);

        checkGlobalConcurrency();
        checkAgentQuota(agent, agentCode);
        checkAccountQuota(loginUserId);

        holdConcurrency(agentCode, loginUserId);
        bumpQps(AgentQuotaKeys.agentQps(agentCode), agent.getQuotaQps());
        if (loginUserId != null) {
            int accountQps = sysConfigPort.getInt(SysConfigKeys.QUOTA_ACCOUNT_QPS, DEFAULT_ACCOUNT_QPS);
            bumpQps(AgentQuotaKeys.accountQps(loginUserId), accountQps > 0 ? accountQps : null);
        }
        log.info("配额 acquire 通过: agentCode={}, loginUserId={}", agentCode, loginUserId);
    }

    /**
     * 占用配额；超限时写入 FAILED 拒绝运行并原样抛出。
     *
     * @param loginUserId    登录用户 id，可为 null
     * @param agent          Agent
     * @param mode           编排模式
     * @param conversationId 会话 ID
     * @param userMessage    用户原话
     */
    public void acquireOrReject(Long loginUserId, AiAgent agent, OrchestrateModeEnum mode,
                                String conversationId, String userMessage) {
        try {
            acquire(loginUserId, agent);
        } catch (BusinessException e) {
            if (CommonErrorCode.TOO_MANY_REQUESTS.getCode().equals(e.getCode()) && agent != null) {
                log.warn("配额拒绝并记运行: agentCode={}, userId={}, msg={}",
                        agent.getAgentCode(), loginUserId, e.getMessage());
                agentRunService.reject(agent.getId(), agent.getAgentCode(), mode,
                        conversationId, userMessage, loginUserId, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 运行结束释放并发占用。
     *
     * @param loginUserId 登录用户 id，可为 null
     * @param agentCode   Agent 编码
     */
    public void release(Long loginUserId, String agentCode) {
        String code = AgentQuotaKeys.normalizeAgentCode(agentCode);
        log.info("配额 release: agentCode={}, loginUserId={}", code, loginUserId);
        String agentConc = AgentQuotaKeys.agentConcurrency(code);
        if (agentConc != null) {
            decConcurrency(agentConc);
        }
        if (loginUserId != null) {
            decConcurrency(AgentQuotaKeys.accountConcurrency(loginUserId));
        }
        decConcurrency(AgentQuotaKeys.GLOBAL_CONCURRENCY);
    }

    /**
     * 运行结束后累计日 token（用于后续日限额校验）。
     *
     * @param loginUserId 登录用户 id，可为 null
     * @param agentCode   Agent 编码
     * @param tokens      本次消耗 token；≤0 忽略
     */
    public void recordTokens(Long loginUserId, String agentCode, int tokens) {
        if (tokens <= 0) {
            return;
        }
        String date = LocalDate.now().toString();
        String agentKey = AgentQuotaKeys.agentDailyTokens(agentCode, date);
        if (agentKey != null) {
            incrDailyTokens(agentKey, tokens);
        }
        if (loginUserId != null) {
            incrDailyTokens(AgentQuotaKeys.accountDailyTokens(loginUserId, date), tokens);
        }
        log.info("配额 recordTokens: agentCode={}, loginUserId={}, tokens={}",
                AgentQuotaKeys.normalizeAgentCode(agentCode), loginUserId, tokens);
    }

    /** 校验全站并发（DB RUNNING + 可选进程内在跑数）。 */
    private void checkGlobalConcurrency() {
        int limit = sysConfigPort.getInt(SysConfigKeys.QUOTA_GLOBAL_CONCURRENCY, DEFAULT_GLOBAL_CONCURRENCY);
        if (limit <= 0) {
            return;
        }
        long dbRunning = countRunning(null, null);
        int inFlight = peekRunningExecutions();
        // 进程内/Redis 计数器也要算进来：多实例下 DB RUNNING 有落库延迟，只看 DB 会放行超额请求
        int held = peekConcurrency(AgentQuotaKeys.GLOBAL_CONCURRENCY);
        long current = Math.max(dbRunning, Math.max(inFlight, held));
        if (current >= limit) {
            log.warn("全站并发超限: current={}, limit={}, dbRunning={}, inFlight={}, held={}",
                    current, limit, dbRunning, inFlight, held);
            throwQuota("全站并发超限");
        }
    }

    /**
     * 读取运行期在跑数；失败返回 0。
     * <p>
     * 只依赖 {@link GraphRuntime} 窄接口，不引用 WorkflowCompiler 实现类，
     * 以便 runtime 内部重构时不影响配额模块。
     * </p>
     *
     * @return 在跑数
     */
    private int peekRunningExecutions() {
        GraphRuntime runtime = graphRuntimeProvider.getIfAvailable();
        if (runtime == null) {
            return 0;
        }
        try {
            return runtime.getRunningExecutionCount();
        } catch (Exception e) {
            log.warn("读取图执行在跑数失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 校验 Agent 层 QPS / 并发 / 日 token。 */
    private void checkAgentQuota(AiAgent agent, String code) {
        if (code == null) {
            return;
        }
        if (agent.getQuotaQps() != null && agent.getQuotaQps() > 0) {
            if (peekQps(AgentQuotaKeys.agentQps(code)) >= agent.getQuotaQps()) {
                log.warn("Agent QPS 超限: agentCode={}, limit={}", code, agent.getQuotaQps());
                throwQuota("Agent QPS 超限: " + code);
            }
        }
        if (agent.getQuotaConcurrency() != null && agent.getQuotaConcurrency() > 0) {
            long running = countRunning(null, code);
            int held = peekConcurrency(AgentQuotaKeys.agentConcurrency(code));
            if (Math.max(running, held) >= agent.getQuotaConcurrency()) {
                log.warn("Agent 并发超限: agentCode={}, limit={}, running={}, held={}",
                        code, agent.getQuotaConcurrency(), running, held);
                throwQuota("Agent 并发超限: " + code);
            }
        }
        if (agent.getQuotaDailyTokens() != null && agent.getQuotaDailyTokens() > 0) {
            String date = LocalDate.now().toString();
            String tokenKey = AgentQuotaKeys.agentDailyTokens(code, date);
            long cached = peekDailyTokens(tokenKey);
            // 计数器有值就以它为准；为 0 才回落到今日已落库合计。
            // 两个来源相加会重复计算（recordTokens 写计数器的同时，运行行的 total_tokens 也落了库）。
            long used = cached > 0 ? cached : sumTokensToday(code, null);
            if (used >= agent.getQuotaDailyTokens()) {
                log.warn("Agent 日 token 超限: agentCode={}, used={}, limit={}",
                        code, used, agent.getQuotaDailyTokens());
                throwQuota("Agent 日 token 超限: " + code);
            }
        }
    }

    /** 校验账号层 QPS / 并发 / 日 token。 */
    private void checkAccountQuota(Long loginUserId) {
        if (loginUserId == null) {
            return;
        }
        int qps = sysConfigPort.getInt(SysConfigKeys.QUOTA_ACCOUNT_QPS, DEFAULT_ACCOUNT_QPS);
        if (qps > 0 && peekQps(AgentQuotaKeys.accountQps(loginUserId)) >= qps) {
            log.warn("账号 QPS 超限: userId={}, limit={}", loginUserId, qps);
            throwQuota("账号 QPS 超限");
        }
        int conc = sysConfigPort.getInt(SysConfigKeys.QUOTA_ACCOUNT_CONCURRENCY, DEFAULT_ACCOUNT_CONCURRENCY);
        if (conc > 0) {
            long running = countRunning(loginUserId, null);
            int held = peekConcurrency(AgentQuotaKeys.accountConcurrency(loginUserId));
            if (Math.max(running, held) >= conc) {
                log.warn("账号并发超限: userId={}, limit={}, running={}, held={}",
                        loginUserId, conc, running, held);
                throwQuota("账号并发超限");
            }
        }
        int daily = sysConfigPort.getInt(SysConfigKeys.QUOTA_ACCOUNT_DAILY_TOKENS, 0);
        if (daily > 0) {
            String date = LocalDate.now().toString();
            String tokenKey = AgentQuotaKeys.accountDailyTokens(loginUserId, date);
            long cached = peekDailyTokens(tokenKey);
            long used = cached > 0 ? cached : sumTokensToday(null, loginUserId);
            if (used >= daily) {
                log.warn("账号日 token 超限: userId={}, used={}, limit={}", loginUserId, used, daily);
                throwQuota("账号日 token 超限");
            }
        }
    }

    /** 占用全局 / Agent / 账号并发槽。 */
    private void holdConcurrency(String agentCode, Long loginUserId) {
        incConcurrency(AgentQuotaKeys.GLOBAL_CONCURRENCY);
        String agentConc = AgentQuotaKeys.agentConcurrency(agentCode);
        if (agentConc != null) {
            incConcurrency(agentConc);
        }
        if (loginUserId != null) {
            incConcurrency(AgentQuotaKeys.accountConcurrency(loginUserId));
        }
    }

    /**
     * 统计 RUNNING 数量。
     *
     * @param creatorId 创建人过滤，可为 null
     * @param agentCode Agent 编码过滤，可为 null
     */
    private long countRunning(Long creatorId, String agentCode) {
        LambdaQueryWrapper<AgentWorkflowRun> q = new LambdaQueryWrapper<AgentWorkflowRun>()
                .eq(AgentWorkflowRun::getStatus, GraphRunStatusEnum.RUNNING.getCode())
                .eq(creatorId != null, AgentWorkflowRun::getCreatorId, creatorId)
                .eq(StringUtils.hasText(agentCode), AgentWorkflowRun::getAgentCode, agentCode);
        Long count = runMapper.selectCount(q);
        return count != null ? count : 0L;
    }

    /**
     * 今日已落库 token 合计（作为 Redis 未命中时的兜底）。
     * <p>
     * 用库侧 {@code SUM} 聚合：配额校验在每次请求的入口都会跑，若把当天全部运行行拉回 JVM 再求和，
     * 调用量一上来就是稳定的性能与内存负担。这里只取回一行聚合结果。
     * </p>
     *
     * @param agentCode Agent 编码，可为 null
     * @param creatorId 创建人，可为 null
     * @return 今日 token 合计；无数据时 0
     */
    private long sumTokensToday(String agentCode, Long creatorId) {
        LocalDateTime from = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        QueryWrapper<AgentWorkflowRun> q = new QueryWrapper<>();
        q.select("COALESCE(SUM(total_tokens), 0) AS total")
                .ge("create_time", from);
        if (StringUtils.hasText(agentCode)) {
            q.eq("agent_code", agentCode);
        }
        if (creatorId != null) {
            q.eq("creator_id", creatorId);
        }
        List<Map<String, Object>> rows = runMapper.selectMaps(q);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return 0L;
        }
        // 结果只有一列；按"取第一个数值"读取，避免依赖驱动对别名的字母大小写处理
        for (Object v : rows.get(0).values()) {
            if (v instanceof Number n) {
                return n.longValue();
            }
        }
        log.warn("sumTokensToday 未取到数值列: agentCode={}, creatorId={}, row={}", agentCode, creatorId, rows.get(0));
        return 0L;
    }

    /** 抛出配额超限业务异常。 */
    private void throwQuota(String detail) {
        String msg = AgentRunErrorCode.QUOTA_EXCEEDED.getLabel() + ": " + detail;
        throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS.getCode(), msg);
    }

    // ===== Redis / 内存计数 =====

    /** 读取当前 1 秒窗口内 QPS 计数。 */
    private int peekQps(String key) {
        if (key == null) {
            return 0;
        }
        cleanupMemory();
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(key);
                return v != null ? Integer.parseInt(v) : 0;
            } catch (Exception e) {
                log.warn("Redis peekQps 失败 key={}: {}", key, e.getMessage());
            }
        }
        long[] slot = memoryQps.get(key);
        if (slot == null) {
            return 0;
        }
        if (System.currentTimeMillis() - slot[0] >= QPS_WINDOW_MS) {
            return 0;
        }
        return (int) slot[1];
    }

    /**
     * 增加 QPS 计数；limit 为 null 或 ≤0 时仅打点不拒绝（拒绝已在 check 阶段完成）。
     *
     * @param key   Redis/内存键
     * @param limit 上限；null/≤0 表示不限但仍可记录
     */
    private void bumpQps(String key, Integer limit) {
        if (key == null || (limit != null && limit <= 0)) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long n = redis.opsForValue().increment(key);
                if (n != null && n == 1L) {
                    redis.expire(key, QPS_WINDOW_MS, TimeUnit.MILLISECONDS);
                }
                return;
            } catch (Exception e) {
                log.warn("Redis bumpQps 失败 key={}: {}", key, e.getMessage());
            }
        }
        long now = System.currentTimeMillis();
        memoryQps.compute(key, (k, old) -> {
            if (old == null || now - old[0] >= QPS_WINDOW_MS) {
                return new long[]{now, 1L};
            }
            old[1]++;
            return old;
        });
    }

    /** 读取并发占用。 */
    private int peekConcurrency(String key) {
        if (key == null) {
            return 0;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(key);
                return v != null ? Integer.parseInt(v) : 0;
            } catch (Exception e) {
                log.warn("Redis peekConcurrency 失败 key={}: {}", key, e.getMessage());
            }
        }
        AtomicInteger n = memoryConcurrency.get(key);
        return n != null ? n.get() : 0;
    }

    /** 并发 +1。 */
    private void incConcurrency(String key) {
        if (key == null) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForValue().increment(key);
                redis.expire(key, CONCURRENCY_TTL_HOURS, TimeUnit.HOURS);
                return;
            } catch (Exception e) {
                log.warn("Redis incConcurrency 失败 key={}: {}", key, e.getMessage());
            }
        }
        memoryConcurrency.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    /** 并发 -1（不低于 0）。 */
    private void decConcurrency(String key) {
        if (key == null) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long n = redis.opsForValue().decrement(key);
                if (n != null && n < 0) {
                    redis.opsForValue().set(key, "0");
                }
                return;
            } catch (Exception e) {
                log.warn("Redis decConcurrency 失败 key={}: {}", key, e.getMessage());
            }
        }
        AtomicInteger n = memoryConcurrency.get(key);
        if (n != null) {
            n.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    /** 读取日 token 计数器。 */
    private long peekDailyTokens(String key) {
        if (key == null) {
            return 0L;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(key);
                return v != null ? Long.parseLong(v) : 0L;
            } catch (Exception e) {
                log.warn("Redis peekDailyTokens 失败 key={}: {}", key, e.getMessage());
            }
        }
        long[] slot = memoryDailyTokens.get(key);
        if (slot == null || System.currentTimeMillis() > slot[0]) {
            return 0L;
        }
        return slot[1];
    }

    /** 日 token +delta，TTL 48h。 */
    private void incrDailyTokens(String key, int delta) {
        if (key == null) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long n = redis.opsForValue().increment(key, delta);
                if (n != null && n == (long) delta) {
                    redis.expire(key, DAILY_TOKEN_TTL_HOURS, TimeUnit.HOURS);
                }
                return;
            } catch (Exception e) {
                log.warn("Redis incrDailyTokens 失败 key={}: {}", key, e.getMessage());
            }
        }
        long expireAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(DAILY_TOKEN_TTL_HOURS);
        memoryDailyTokens.compute(key, (k, old) -> {
            if (old == null || System.currentTimeMillis() > old[0]) {
                return new long[]{expireAt, delta};
            }
            old[1] += delta;
            return old;
        });
    }

    /** 清理过期内存 QPS / 日 token 条目。 */
    private void cleanupMemory() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, long[]>> qpsIt = memoryQps.entrySet().iterator();
        while (qpsIt.hasNext()) {
            Map.Entry<String, long[]> e = qpsIt.next();
            if (now - e.getValue()[0] > MEMORY_ENTRY_TTL_MS) {
                qpsIt.remove();
            }
        }
        Iterator<Map.Entry<String, long[]>> tokIt = memoryDailyTokens.entrySet().iterator();
        while (tokIt.hasNext()) {
            Map.Entry<String, long[]> e = tokIt.next();
            if (now > e.getValue()[0]) {
                tokIt.remove();
            }
        }
        // 并发计数没有过期时间；归零的条目留着会随 key 数量无限增长
        memoryConcurrency.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }
}
