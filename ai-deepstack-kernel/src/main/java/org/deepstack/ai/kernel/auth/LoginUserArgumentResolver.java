package org.deepstack.ai.kernel.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 {@link LoginUser}：从请求 Bearer Token 到 {@link LoginSessionStore}（Redis）取当前用户。
 */
@Slf4j
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final LoginSessionStore loginSessionStore;

    public LoginUserArgumentResolver(LoginSessionStore loginSessionStore) {
        this.loginSessionStore = loginSessionStore;
    }

    /**
     * 参数带 {@link LoginUser} 且类型为 {@link AppUserInfo} 时生效。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && AppUserInfo.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 解析当前登录用户；required 且会话不存在时抛 NEED_LOGIN。
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        LoginUser ann = parameter.getParameterAnnotation(LoginUser.class);
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String token = BearerTokens.resolve(request);
        AppUserInfo user = token == null ? null : loginSessionStore.get(token);
        if (user == null) {
            if (ann != null && ann.required()) {
                log.warn("Login required but session missing in Redis");
                BusinessException.of(CommonErrorCode.NEED_LOGIN);
            }
            return null;
        }
        return user;
    }
}
