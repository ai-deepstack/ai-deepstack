package org.deepstack.ai.kernel.config;

import org.apache.ibatis.plugin.Interceptor;
import org.deepstack.ai.kernel.p6spy.MapperIdInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P6Spy 配套：把 Mapper id 写入 ThreadLocal，供单行 SQL 日志引用。
 */
@Configuration
public class P6SpyConfiguration {

    /** 装配 SQL 日志用的 Mapper 标识拦截器。 */
    @Bean
    public Interceptor mapperIdInterceptor() {
        return new MapperIdInterceptor();
    }
}
