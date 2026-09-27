package org.deepstack.ai.kernel.web;

import org.deepstack.ai.kernel.model.RequestInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求 URI 写入 MDC，并初始化 {@link RequestInfo} 计时。
 * <p>
 * {@code traceId} / {@code spanId} 由 Micrometer Tracing 写入 MDC，此处不生成、不覆盖。
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestInfoFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_URI = "requestURI";

    /** 记录请求信息并继续过滤器链。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RequestInfo.init();
        MDC.put(MDC_REQUEST_URI, request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_URI);
            RequestInfo.remove();
        }
    }
}
