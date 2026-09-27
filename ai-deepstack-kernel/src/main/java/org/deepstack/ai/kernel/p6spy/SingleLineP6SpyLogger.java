package org.deepstack.ai.kernel.p6spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.springframework.util.StringUtils;

/**
 * P6Spy 单行日志：耗时、Mapper 简名.方法、压缩后的 SQL。
 * <p>在 {@code spy.properties} 中配置：
 * {@code logMessageFormat=org.deepstack.ai.kernel.p6spy.SingleLineP6SpyLogger}</p>
 */
public class SingleLineP6SpyLogger implements MessageFormattingStrategy {

    /** 把 SQL 日志格式化为单行。 */
    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category,
                                String prepared, String sql, String url) {
        if (!StringUtils.hasText(sql)) {
            return "";
        }
        String mapper = toSimpleMapper(MybatisSqlContext.peek());
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return elapsed + " ms | " + mapper + " | " + oneLine;
    }

    /** 将 {@code com.xxx.FooMapper.method} 转为 {@code FooMapper.method}。 */
    static String toSimpleMapper(String mapperId) {
        if (!StringUtils.hasText(mapperId)) {
            return "-";
        }
        int methodDot = mapperId.lastIndexOf('.');
        if (methodDot <= 0 || methodDot == mapperId.length() - 1) {
            return mapperId;
        }
        String method = mapperId.substring(methodDot + 1);
        String namespace = mapperId.substring(0, methodDot);
        int classDot = namespace.lastIndexOf('.');
        String simpleClass = classDot >= 0 ? namespace.substring(classDot + 1) : namespace;
        return simpleClass + "." + method;
    }
}
