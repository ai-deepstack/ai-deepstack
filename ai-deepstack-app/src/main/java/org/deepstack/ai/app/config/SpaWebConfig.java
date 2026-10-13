package org.deepstack.ai.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA fallback：前端路由转发到 index.html（静态资源与 /api 仍由默认处理）。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    /**
     * 注册前端路由 fallback 到 index.html。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        String index = "forward:/index.html";
        registry.addViewController("/").setViewName(index);
        registry.addViewController("/login").setViewName(index);
        registry.addViewController("/playground").setViewName(index);
        registry.addViewController("/runs").setViewName(index);
        registry.addViewController("/alerts").setViewName(index);
        registry.addViewController("/agents").setViewName(index);
        registry.addViewController("/agents/{id}").setViewName(index);
        registry.addViewController("/agents/{id}/stats").setViewName(index);
        registry.addViewController("/agents/{id}/graph").setViewName(index);
        registry.addViewController("/models").setViewName(index);
        registry.addViewController("/tools").setViewName(index);
        registry.addViewController("/mcp").setViewName(index);
        registry.addViewController("/intents").setViewName(index);
        registry.addViewController("/knowledge").setViewName(index);
        registry.addViewController("/knowledge/{baseCode}/graph").setViewName(index);
        registry.addViewController("/settings").setViewName(index);
    }
}
