package org.deepstack.ai.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA fallback：前端路由转发到 index.html。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    /**
     * 注册前端路由 fallback 到 index.html。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/agents").setViewName("forward:/index.html");
        registry.addViewController("/models").setViewName("forward:/index.html");
        registry.addViewController("/playground").setViewName("forward:/index.html");
    }
}
