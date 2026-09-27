package org.deepstack.ai.kernel.config;

import org.deepstack.ai.kernel.auth.LoginUserArgumentResolver;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Kernel 自动配置：扫描组件并注册 {@link LoginUserArgumentResolver}。
 */
@Configuration
@ComponentScan("org.deepstack.ai.kernel")
public class KernelAutoConfiguration implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;

    public KernelAutoConfiguration(LoginUserArgumentResolver loginUserArgumentResolver) {
        this.loginUserArgumentResolver = loginUserArgumentResolver;
    }

    /**
     * 注册登录用户参数解析器。
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
