package org.deepstack.ai.app.config;

import lombok.RequiredArgsConstructor;
import org.deepstack.ai.app.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器（默认保护 {@code /api/**}，白名单用 {@code @Anonymous}）。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    /** 注册登录拦截器。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/error",
                        "/actuator/**"
                )
                .order(0);
    }
}
