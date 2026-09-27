package org.deepstack.ai.agent.observability;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.kernel.observability.TrajectoryRedactTokens;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 运行轨迹脱敏：落库前遮盖手机号 / API Key / Bearer，并按配置截断长度。
 * <p>
 * 开关与长度见 {@link SysConfigKeys#RUN_REDACT_ENABLED} 等；关闭时原样返回。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrajectoryRedactor {

    private static final String TRUNCATE_SUFFIX = TrajectoryRedactTokens.TRUNCATE_SUFFIX;
    private static final int STAGES_STRING_MAX = 64;
    private static final int DEFAULT_MAX_USER_MESSAGE = 2000;
    private static final int DEFAULT_MAX_RESULT = 4000;
    private static final int DEFAULT_MAX_NODE_IO = 1000;

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern API_KEY = Pattern.compile("sk-[A-Za-z0-9]{8,}");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-+/=]+");

    private final SysConfigPort sysConfigPort;

    /**
     * 对文本做敏感信息遮盖并按 maxLen 截断；null 原样返回。
     *
     * @param text   原文，可为 null
     * @param maxLen 最大保留字符数；≤0 表示仅遮盖不截断
     * @return 脱敏后文本
     */
    public String redactText(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        if (!isEnabled()) {
            return text;
        }
        int before = text.length();
        String masked = maskSecrets(text);
        String out = truncate(masked, maxLen);
        if (out.length() != before) {
            log.debug("TrajectoryRedactor redactText: before={}, after={}, maxLen={}", before, out.length(), maxLen);
        }
        return out;
    }

    /**
     * 按配置长度脱敏用户消息。
     *
     * @param userMessage 用户原话，可为 null
     * @return 脱敏后文本
     */
    public String redactUserMessage(String userMessage) {
        if (!isEnabled()) {
            return userMessage;
        }
        int max = sysConfigPort.getInt(SysConfigKeys.RUN_REDACT_MAX_USER_MESSAGE, DEFAULT_MAX_USER_MESSAGE);
        log.info("TrajectoryRedactor redactUserMessage: maxLen={}, inputLen={}",
                max, userMessage != null ? userMessage.length() : 0);
        return redactText(userMessage, max);
    }

    /**
     * 按配置长度脱敏运行结果。
     *
     * @param result 结果文本，可为 null
     * @return 脱敏后文本
     */
    public String redactResult(String result) {
        if (!isEnabled()) {
            return result;
        }
        int max = sysConfigPort.getInt(SysConfigKeys.RUN_REDACT_MAX_RESULT, DEFAULT_MAX_RESULT);
        log.info("TrajectoryRedactor redactResult: maxLen={}, inputLen={}",
                max, result != null ? result.length() : 0);
        return redactText(result, max);
    }

    /**
     * 脱敏节点执行时间线：遍历 List/Map 或 JSON 字符串中的 input / output / error / errorMessage。
     *
     * @param nodeExecutions 节点时间线对象或 JSON，可为 null
     * @return 脱敏后的同构对象；解析失败时返回原值
     */
    public Object redactNodeExecutions(Object nodeExecutions) {
        if (nodeExecutions == null || !isEnabled()) {
            return nodeExecutions;
        }
        int maxIo = sysConfigPort.getInt(SysConfigKeys.RUN_REDACT_MAX_NODE_IO, DEFAULT_MAX_NODE_IO);
        try {
            Object parsed = coerceToStructure(nodeExecutions);
            Object redacted = walkNodeExecutions(parsed, maxIo);
            log.info("TrajectoryRedactor redactNodeExecutions: maxIo={}, type={}",
                    maxIo, nodeExecutions.getClass().getSimpleName());
            if (nodeExecutions instanceof String) {
                return JSON.toJSONString(redacted);
            }
            return redacted;
        } catch (Exception e) {
            log.warn("TrajectoryRedactor redactNodeExecutions parse failed: err={}", e.getMessage());
            return nodeExecutions;
        }
    }

    /**
     * 脱敏 stages：保留数值；字符串按最长 64 做脱敏截断。
     *
     * @param stages 阶段耗时 map / JSON，可为 null
     * @return 脱敏后的同构对象；解析失败时返回原值
     */
    public Object redactStages(Object stages) {
        if (stages == null || !isEnabled()) {
            return stages;
        }
        try {
            Object parsed = coerceToStructure(stages);
            Object redacted = walkStages(parsed);
            log.info("TrajectoryRedactor redactStages: type={}", stages.getClass().getSimpleName());
            if (stages instanceof String) {
                return JSON.toJSONString(redacted);
            }
            return redacted;
        } catch (Exception e) {
            log.warn("TrajectoryRedactor redactStages parse failed: err={}", e.getMessage());
            return stages;
        }
    }

    /**
     * 是否启用脱敏。
     *
     * @return true 表示开启
     */
    private boolean isEnabled() {
        return sysConfigPort.isYes(SysConfigKeys.RUN_REDACT_ENABLED);
    }

    /**
     * 遮盖手机号、API Key、Bearer token。
     *
     * @param text 原文
     * @return 遮盖后文本
     */
    private static String maskSecrets(String text) {
        String s = PHONE.matcher(text).replaceAll(TrajectoryRedactTokens.PHONE);
        s = API_KEY.matcher(s).replaceAll(TrajectoryRedactTokens.API_KEY);
        s = BEARER.matcher(s).replaceAll(TrajectoryRedactTokens.BEARER);
        return s;
    }

    /**
     * 超长截断并追加后缀。
     *
     * @param text   文本
     * @param maxLen 上限；≤0 不截断
     * @return 截断结果
     */
    private static String truncate(String text, int maxLen) {
        if (maxLen <= 0 || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + TRUNCATE_SUFFIX;
    }

    /**
     * 将 JSON 字符串解析为结构，已是 List/Map 则原样返回。
     *
     * @param value 输入
     * @return List/Map 或其他原值
     */
    private Object coerceToStructure(Object value) {
        if (value instanceof String s) {
            if (!StringUtils.hasText(s)) {
                return s;
            }
            String trimmed = s.trim();
            if (trimmed.startsWith("[")) {
                return JSON.parseObject(trimmed, new TypeReference<List<Object>>() {});
            }
            if (trimmed.startsWith("{")) {
                return JSON.parseObject(trimmed, new TypeReference<Map<String, Object>>() {});
            }
            return s;
        }
        return value;
    }

    /**
     * 递归处理节点执行结构。
     *
     * @param value 结构
     * @param maxIo 文本字段上限
     * @return 脱敏结构
     */
    @SuppressWarnings("unchecked")
    private Object walkNodeExecutions(Object value, int maxIo) {
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(walkNodeExecutions(item, maxIo));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (isNodeTextField(key) && v instanceof String str) {
                    out.put(key, redactText(str, maxIo));
                } else {
                    out.put(key, walkNodeExecutions(v, maxIo));
                }
            }
            return out;
        }
        return value;
    }

    /**
     * 判断是否为需脱敏的节点文本字段。
     *
     * @param key 字段名
     * @return true 表示需脱敏
     */
    private static boolean isNodeTextField(String key) {
        return TrajectoryRedactTokens.NODE_INPUT.equals(key)
                || TrajectoryRedactTokens.NODE_OUTPUT.equals(key)
                || TrajectoryRedactTokens.NODE_ERROR.equals(key)
                || TrajectoryRedactTokens.NODE_ERROR_MESSAGE.equals(key);
    }

    /**
     * 递归处理 stages：数值保留，长字符串截断。
     *
     * @param value 结构
     * @return 脱敏结构
     */
    @SuppressWarnings("unchecked")
    private Object walkStages(Object value) {
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(walkStages(item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (v instanceof String str) {
                    out.put(key, redactText(str, STAGES_STRING_MAX));
                } else if (v instanceof Number || v instanceof Boolean || v == null) {
                    out.put(key, v);
                } else {
                    out.put(key, walkStages(v));
                }
            }
            return out;
        }
        if (value instanceof String str) {
            return redactText(str, STAGES_STRING_MAX);
        }
        return value;
    }
}
