package org.deepstack.ai.app.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.app.settings.mapper.SysConfigMapper;
import org.deepstack.ai.app.settings.model.dto.response.SysConfigResponse;
import org.deepstack.ai.app.settings.model.entity.SysConfig;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.model.ModelTypeEnum;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App 侧 sys_config：读路径 Redis→DB→yml→coded；写路径库 + Redis SET/DEL + invalidate PUBLISH。
 */
@Slf4j
@Primary
@Service
public class SysConfigPortImpl implements SysConfigPort, SettingsService {

    public static final String REDIS_KEY_PREFIX = "deepstack:sys_config:";
    public static final String INVALIDATE_CHANNEL = "deepstack:sys_config:invalidate";

    private static final Map<String, String> CONFIG_NAMES = Map.ofEntries(
            Map.entry(SysConfigKeys.GRAPH_ENABLED, "图谱总开关"),
            Map.entry(SysConfigKeys.MEMORY_ENABLED, "长期记忆总开关"),
            Map.entry(SysConfigKeys.MEMORY_GRAPH_BY_DEFAULT, "记忆默认走图"),
            Map.entry(SysConfigKeys.MCP_ENABLED, "MCP 总开关"),
            Map.entry(SysConfigKeys.MCP_TOOL_CODE_PREFIX, "MCP 工具编码前缀"),
            Map.entry(SysConfigKeys.TOOLS_DISCLOSURE_MODE, "工具披露模式"),
            Map.entry(SysConfigKeys.TOOLS_PROGRESSIVE_FULL_BELOW, "渐进披露全量阈值"),
            Map.entry(SysConfigKeys.TOOLS_SEARCH_TOP_K, "工具搜索条数"),
            Map.entry(SysConfigKeys.CHECKPOINT_ENABLED, "Checkpoint 开关"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_HOPS, "图谱扩边跳数"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_MAX_CHUNKS_PER_EXTRACT, "建图每批 chunk 数"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_CHAT_MODEL_CODE, "图谱抽取模型"),
            Map.entry(SysConfigKeys.KNOWLEDGE_GRAPH_EXTRA_TOP_K, "图扩展追加上限"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_KB_CONCURRENCY, "多库召回并发"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_MAX_EXPAND_CONCURRENCY, "扩边并发"),
            Map.entry(SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, "单库召回超时"),
            Map.entry(SysConfigKeys.LLM_CONNECT_TIMEOUT_MS, "模型连接超时"),
            Map.entry(SysConfigKeys.LLM_READ_TIMEOUT_MS, "模型读超时"),
            Map.entry(SysConfigKeys.MEMORY_RECALL_MAX_EXPAND_CONCURRENCY, "记忆扩边并发"),
            Map.entry(SysConfigKeys.MCP_BOOTSTRAP_MAX_CONCURRENCY, "MCP启动建连并发"),
            Map.entry(SysConfigKeys.CHAT_CONTEXT_TIMEOUT_MS, "CHAT拼prompt总超时"),
            Map.entry(SysConfigKeys.RUN_REDACT_ENABLED, "轨迹脱敏开关"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_USER_MESSAGE, "用户消息保留长度"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_RESULT, "结果保留长度"),
            Map.entry(SysConfigKeys.RUN_REDACT_MAX_NODE_IO, "节点输入输出保留长度"),
            Map.entry(SysConfigKeys.QUOTA_GLOBAL_CONCURRENCY, "全站并发上限"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_QPS, "账号 QPS"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_CONCURRENCY, "账号并发"),
            Map.entry(SysConfigKeys.QUOTA_ACCOUNT_DAILY_TOKENS, "账号每日 token"),
            Map.entry(SysConfigKeys.HITL_TIMEOUT_MINUTES, "HITL 全局超时分钟"),
            Map.entry(SysConfigKeys.ALERT_ENABLED, "告警开关"),
            Map.entry(SysConfigKeys.ALERT_WEBHOOK_URL, "告警 Webhook"),
            Map.entry(SysConfigKeys.ALERT_ERROR_RATE_THRESHOLD, "错误率阈值"),
            Map.entry(SysConfigKeys.ALERT_P95_MS_THRESHOLD, "P95 延迟阈值毫秒"),
            Map.entry(SysConfigKeys.ALERT_HITL_BACKLOG_THRESHOLD, "HITL 积压阈值"),
            Map.entry(SysConfigKeys.ALERT_RUNNING_THRESHOLD, "在跑数阈值"),
            Map.entry(SysConfigKeys.ALERT_WINDOW_MINUTES, "告警统计窗口分钟")
    );

    private static final Map<String, Integer> MODEL_TYPE_BY_KEY = Map.of(
            SysConfigKeys.KNOWLEDGE_GRAPH_CHAT_MODEL_CODE, ModelTypeEnum.CHAT.getCode()
    );

    private static final Set<String> KNOWN_KEYS = SysConfigFallback.CODED_DEFAULTS.keySet();

    private final SysConfigMapper sysConfigMapper;
    private final Environment environment;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public SysConfigPortImpl(SysConfigMapper sysConfigMapper,
                             Environment environment,
                             ObjectProvider<StringRedisTemplate> redisProvider) {
        this.sysConfigMapper = sysConfigMapper;
        this.environment = environment;
        this.redisProvider = redisProvider;
    }

    /** 返回 String。 */
    @Override
    public String getString(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        String cached = redisGet(key);
        if (cached != null) {
            return cached;
        }
        SysConfig row = findByKey(key);
        if (row != null && StringUtils.hasText(row.getConfigValue())) {
            String dbVal = row.getConfigValue().trim();
            if (SysConfigFallback.VALUE_TYPE_YES_NO.equals(SysConfigFallback.VALUE_TYPES.get(key))) {
                dbVal = SysConfigFallback.normalizeYesNoStored(dbVal);
            }
            redisSet(key, dbVal);
            return dbVal;
        }
        String fallback = SysConfigFallback.resolveFallback(environment, key);
        return fallback != null ? fallback : "";
    }

    /** 返回 Int（默认值见 {@link SysConfigFallback#CODED_DEFAULTS}）。 */
    @Override
    public int getInt(String key) {
        return SysConfigFallback.resolveInt(getString(key), key);
    }

    /** 返回 Int。 */
    @Override
    public int getInt(String key, int defaultValue) {
        return SysConfigFallback.parseIntOrDefault(getString(key), defaultValue);
    }

    /** 返回浮点（默认值见 {@link SysConfigFallback#CODED_DEFAULTS}）。 */
    @Override
    public double getDouble(String key) {
        return SysConfigFallback.resolveDouble(getString(key), key);
    }

    /** 是否 Yes。 */
    @Override
    public boolean isYes(String key) {
        return SysConfigFallback.isYesRaw(getString(key));
    }

    /** 列出全部系统配置。 */
    @Override
    public List<SysConfigResponse> listAll() {
        Map<String, SysConfig> byKey = new LinkedHashMap<>();
        List<SysConfig> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getConfigKey));
        if (rows != null) {
            for (SysConfig row : rows) {
                byKey.put(row.getConfigKey(), row);
            }
        }
        List<SysConfigResponse> out = new ArrayList<>();
        for (String key : KNOWN_KEYS.stream().sorted().toList()) {
            SysConfig row = byKey.get(key);
            String type = row != null && StringUtils.hasText(row.getValueType())
                    ? row.getValueType()
                    : SysConfigFallback.VALUE_TYPES.getOrDefault(key, SysConfigFallback.VALUE_TYPE_STRING);

            String source;
            String effective;
            if (row != null && StringUtils.hasText(row.getConfigValue())) {
                source = "db";
                effective = row.getConfigValue().trim();
            } else if (SysConfigFallback.hasYmlProperty(environment, key)) {
                source = "yml";
                effective = SysConfigFallback.resolveFallback(environment, key);
            } else {
                source = "default";
                effective = SysConfigFallback.CODED_DEFAULTS.getOrDefault(key, "");
            }
            if (effective == null) {
                effective = "";
            }
            if (SysConfigFallback.VALUE_TYPE_YES_NO.equals(type) && StringUtils.hasText(effective)) {
                effective = SysConfigFallback.normalizeYesNoStored(effective);
            }

            SysConfigResponse item = new SysConfigResponse();
            if (row != null) {
                item.setId(row.getId());
            }
            item.setConfigKey(key);
            item.setConfigName(resolveConfigName(key, row));
            item.setConfigValue(effective);
            item.setValueType(type);
            item.setDescription(row != null ? row.getDescription() : null);
            item.setGroup(groupOf(key));
            item.setSource(source);
            if (SysConfigFallback.VALUE_TYPE_YES_NO.equals(type)) {
                item.setValueName(YesNo.labelOf(SysConfigFallback.parseYesNoCode(effective)));
            }
            if (SysConfigFallback.VALUE_TYPE_MODEL.equals(type)) {
                item.setModelType(MODEL_TYPE_BY_KEY.get(key));
            }
            out.add(item);
        }
        return out;
    }

    /** 批量更新系统配置并广播失效。 */
    @Override
    @Transactional
    public void updateAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            BusinessException.of(CommonErrorCode.MISSING_PARAM);
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            if (!KNOWN_KEYS.contains(key)) {
                throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "未知配置项: " + key);
            }
            String type = SysConfigFallback.VALUE_TYPES.getOrDefault(key, SysConfigFallback.VALUE_TYPE_STRING);
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (SysConfigFallback.VALUE_TYPE_YES_NO.equals(type)) {
                if (StringUtils.hasText(value)) {
                    value = SysConfigFallback.normalizeYesNoStored(value);
                    if (!"0".equals(value) && !"1".equals(value)) {
                        throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                                "是否类仅允许 0/1: " + key);
                    }
                } else {
                    value = "";
                }
            } else if (SysConfigFallback.VALUE_TYPE_INT.equals(type) && StringUtils.hasText(value)) {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                            "整型配置非法: " + key);
                }
            }
            SysConfig existing = findByKey(key);
            if (existing == null) {
                SysConfig row = new SysConfig();
                row.setConfigKey(key);
                row.setConfigName(CONFIG_NAMES.getOrDefault(key, key));
                row.setConfigValue(value);
                row.setValueType(type);
                sysConfigMapper.insert(row);
            } else {
                if (!StringUtils.hasText(existing.getConfigName())) {
                    existing.setConfigName(CONFIG_NAMES.getOrDefault(key, key));
                }
                existing.setConfigValue(value);
                existing.setValueType(type);
                sysConfigMapper.updateById(existing);
            }
            if (StringUtils.hasText(value)) {
                redisSet(key, value);
            } else {
                redisDelete(key);
            }
            publishInvalidate(key);
            log.info("sys_config updated: key={}, value={}", key, value);
        }
    }

    /** 按 key 查配置行。 */
    private SysConfig findByKey(String key) {
        return sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .last("LIMIT 1"));
    }

    /** 解析配置展示名。 */
    private static String resolveConfigName(String key, SysConfig row) {
        if (row != null && StringUtils.hasText(row.getConfigName())) {
            return row.getConfigName();
        }
        return CONFIG_NAMES.getOrDefault(key, key);
    }

    /** 按 key 前缀归组。 */
    private static String groupOf(String key) {
        if (key.startsWith("graph.")) {
            return "图谱";
        }
        if (key.startsWith("memory.")) {
            return "长期记忆";
        }
        if (key.startsWith("mcp.") || key.startsWith("tools.")) {
            return "工具 / MCP";
        }
        if (key.startsWith("checkpoint.")) {
            return "Checkpoint";
        }
        if (key.startsWith("knowledge.")) {
            return "知识库召回";
        }
        if (key.startsWith("run.redact.")) {
            return "轨迹脱敏";
        }
        if (key.startsWith("quota.")) {
            return "配额";
        }
        if (key.startsWith("hitl.")) {
            return "人工确认";
        }
        if (key.startsWith("alert.")) {
            return "告警";
        }
        if (key.startsWith("llm.") || key.startsWith("chat.")) {
            return "模型 / 对话";
        }
        return "其它";
    }

    /** 从 Redis 读取配置值。 */
    private String redisGet(String key) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return null;
        }
        try {
            return redis.opsForValue().get(REDIS_KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("sys_config redis get failed: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 写入 Redis 配置缓存。 */
    private void redisSet(String key, String value) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(REDIS_KEY_PREFIX + key, value);
        } catch (Exception e) {
            log.warn("sys_config redis set failed: key={}, err={}", key, e.getMessage());
        }
    }

    /** 删除 Redis 配置缓存。 */
    private void redisDelete(String key) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.delete(REDIS_KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("sys_config redis delete failed: key={}, err={}", key, e.getMessage());
        }
    }

    /** 发布配置失效消息。 */
    private void publishInvalidate(String keyOrStar) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.convertAndSend(INVALIDATE_CHANNEL, keyOrStar != null ? keyOrStar : "*");
        } catch (Exception e) {
            log.warn("sys_config invalidate publish failed: key={}, err={}", keyOrStar, e.getMessage());
        }
    }
}
