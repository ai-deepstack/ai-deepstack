package org.deepstack.ai.app.service;

import org.deepstack.ai.app.model.dto.request.LoginRequest;
import org.deepstack.ai.app.model.dto.response.LoginResponse;

/**
 * 应用认证服务。
 */
public interface AuthService {

    /**
     * 用户名密码登录，签发 JWT 并将用户信息写入 Redis 会话。
     *
     * @param req 登录请求
     * @return token 与用户基本信息
     */
    LoginResponse login(LoginRequest req);

    /**
     * 登出：删除 Redis 中的登录会话。
     *
     * @param token Bearer token，可空
     */
    void logout(String token);
}
