package org.deepstack.ai.app.settings.config;

import org.deepstack.ai.app.settings.YmlSysConfigPort;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 注册 yml 回落 {@link SysConfigPort}（仅当 App 未提供 {@code SysConfigPortImpl} 时）。
 */
@Configuration
public class YmlSysConfigConfiguration {

    /** 装配基于 yml 的配置端口。 */
    @Bean
    @ConditionalOnMissingBean(SysConfigPort.class)
    public SysConfigPort ymlSysConfigPort(Environment environment) {
        return new YmlSysConfigPort(environment);
    }
}
