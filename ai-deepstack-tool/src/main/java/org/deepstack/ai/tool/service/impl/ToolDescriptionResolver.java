package org.deepstack.ai.tool.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具描述解析器
 * <p>
 * 通过 Spring 容器按 Bean 名查找 ToolHandler 实例，
 * 反射读取 {@link Tool} 注解的 description 字段，作为 DB description 为空时的兜底。
 * </p>
 * <p>
 * 解析结果按 {@code handlerBean} 名缓存（Bean 的 @Tool 注解在运行期不变）。
 * </p>
 *
 */
@Slf4j
@Component
public class ToolDescriptionResolver {

    private final ApplicationContext applicationContext;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * @param applicationContext Spring 上下文，用于按 Bean 名解析 ToolHandler
     */
    public ToolDescriptionResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 按 handler_bean 名查找 Bean，反射读取 @Tool 注解的 description。
     *
     * @param handlerBean Spring 容器中的 Bean 名
     * @return Java @Tool 注解的 description；Bean 不存在或方法无 @Tool 注解时返回 null
     */
    public String resolveJavaDescription(String handlerBean) {
        if (handlerBean == null || handlerBean.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(handlerBean, this::doResolve);
    }

    /**
     * 从 Spring Bean 上扫描 {@code @Tool} 注解并取描述。
     *
     * @param handlerBean Bean 名称
     * @return 工具描述；找不到时返回 null
     */
    private String doResolve(String handlerBean) {
        log.debug("ToolDescriptionResolver.doResolve: handlerBean={}", handlerBean);
        try {
            Object bean = applicationContext.getBean(handlerBean);
            if (bean == null) {
                log.warn("ToolHandler bean not found: {}", handlerBean);
                return null;
            }
            // 扫描所有方法，找带 @Tool 注解的
            for (Method method : bean.getClass().getMethods()) {
                Tool toolAnno = method.getAnnotation(Tool.class);
                if (toolAnno != null) {
                    String desc = toolAnno.description();
                    if (desc != null && !desc.isBlank()) {
                        return desc;
                    }
                }
            }
            log.warn("No @Tool annotation found on bean: {}", handlerBean);
            return null;
        } catch (Exception e) {
            log.warn("Failed to resolve @Tool description for bean {}: {}", handlerBean, e.getMessage());
            return null;
        }
    }
}
