package org.deepstack.ai.app.interceptor;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.kernel.auth.Anonymous;
import org.deepstack.ai.kernel.auth.BearerTokens;
import org.deepstack.ai.kernel.auth.LoginSessionStore;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.model.Response;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截：校验 Bearer Token 对应的 Redis 会话是否存在；
 * 默认 {@code /api/**} 需登录，{@link Anonymous} 为白名单。
 * <p>当前用户由 {@code @LoginUser} 从 Redis 解析，不使用 ThreadLocal。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final LoginSessionStore loginSessionStore;

    /**
     * 拦截需登录的 API：校验 Bearer 会话；{@link Anonymous} 放行。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return true 放行；false 已写 401
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        boolean anonymous = isAnonymous(method);
        String token = BearerTokens.resolve(request);
        boolean loggedIn = token != null && loginSessionStore.exists(token);

        if (anonymous) {
            return true;
        }

        if (!loggedIn) {
            log.info("未登录或会话失效: path={}", request.getRequestURI());
            writeNeedLogin(response);
            return false;
        }
        return true;
    }

    /**
     * 方法或类上是否标注 {@link Anonymous}。
     *
     * @param method 处理器方法
     * @return true 表示匿名可访问
     */
    private static boolean isAnonymous(HandlerMethod method) {
        if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), Anonymous.class)) {
            return true;
        }
        return AnnotatedElementUtils.hasAnnotation(method.getBeanType(), Anonymous.class);
    }

    /**
     * 写出统一未登录 JSON 响应。
     *
     * @param response HTTP 响应
     */
    private void writeNeedLogin(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(Response.fail(CommonErrorCode.NEED_LOGIN)));
    }
}
