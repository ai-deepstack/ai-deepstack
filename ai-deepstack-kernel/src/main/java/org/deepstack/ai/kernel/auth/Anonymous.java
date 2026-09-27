package org.deepstack.ai.kernel.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 匿名白名单：标注的接口允许未登录访问。
 * <p>默认 {@code /api/**} 需登录；登录拦截器遇到本注解则跳过强制鉴权。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Anonymous {
}
