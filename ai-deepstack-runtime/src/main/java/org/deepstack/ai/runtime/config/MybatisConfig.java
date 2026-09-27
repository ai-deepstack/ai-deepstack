package org.deepstack.ai.runtime.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描 + 插件。
 * <p>
 * 仅注册带 {@link Mapper} 的接口，避免把业务 SPI（如 LoginSessionStore）误注册为 Bean。
 * 乐观锁须注册，否则带 {@code @Version} 的实体 {@code updateById} 会报
 * {@code MP_OPTLOCK_VERSION_ORIGINAL} 参数缺失。
 * </p>
 */
@Configuration
@MapperScan(basePackages = "org.deepstack.ai", annotationClass = Mapper.class)
public class MybatisConfig {

    /** 装配分页等 MyBatis 拦截器。 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁须在分页之前；无 @Version 字段时无实际开销
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
