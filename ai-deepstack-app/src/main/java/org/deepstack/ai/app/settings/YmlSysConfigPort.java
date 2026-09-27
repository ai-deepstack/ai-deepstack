package org.deepstack.ai.app.settings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 仅读 Environment + 代码默认的 {@link SysConfigPort}；无 App 全量实现时由自动配置注册。
 */
@Slf4j
@RequiredArgsConstructor
public class YmlSysConfigPort implements SysConfigPort {

    private final Environment environment;

    /** 返回 String。 */
    @Override
    public String getString(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
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
}
