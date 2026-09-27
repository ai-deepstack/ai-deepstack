package org.deepstack.ai.card.processor;

import org.deepstack.ai.card.service.spi.CardProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用业务确认卡：只记录用户确认，不执行副作用。真正落地放在 HITL resume 之后的工具节点。
 */
@Slf4j
@Component
public class GenericConfirmCardProcessor implements CardProcessor {

    public static final String CARD_TYPE = "generic_confirm";

    /** {@inheritDoc} */
    @Override
    public String cardType() {
        return CARD_TYPE;
    }

    /**
     * 仅审计确认结果，不执行业务副作用。
     *
     * @param payload 卡片 payload
     * @param userId  用户 ID
     * @return accepted 标记与标题
     */
    @Override
    public Object persist(Map<String, Object> payload, String userId) {
        log.info("generic_confirm persist (audit only): userId={}, title={}",
                userId, payload != null ? payload.get("title") : null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("cardType", CARD_TYPE);
        if (payload != null && payload.get("title") != null) {
            result.put("title", payload.get("title"));
        }
        return result;
    }

    /**
     * 校验 payload 结构（fields 须为对象数组且含 key）。
     *
     * @param payload 待校验 payload
     * @return 校验结果
     */
    @Override
    public ValidationResult validate(Map<String, Object> payload) {
        if (payload == null) {
            log.warn("generic_confirm validate 失败: payload 为空");
            return ValidationResult.fail("payload 不能为空");
        }
        Object fieldsObj = payload.get("fields");
        if (fieldsObj == null) {
            return ValidationResult.success();
        }
        List<Map<String, Object>> fields = coerceFields(fieldsObj);
        if (fields == null) {
            return ValidationResult.fail("fields 须为对象数组");
        }
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> field = fields.get(i);
            if (field == null || !StringUtils.hasText(str(field.get("key")))) {
                return ValidationResult.fail("fields[" + i + "] 缺少 key");
            }
        }
        return ValidationResult.success();
    }

    /**
     * 将 fields 对象强制转为 Map 列表。
     *
     * @param fieldsObj 原始 fields（须为 List）
     * @return 字段列表；类型不匹配时 null
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> coerceFields(Object fieldsObj) {
        if (fieldsObj instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) m));
                } else {
                    return null;
                }
            }
            return out;
        }
        return null;
    }

    /** 对象转字符串；null 保持 null。 */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
