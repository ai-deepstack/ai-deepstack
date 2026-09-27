package org.deepstack.ai.app.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.app.model.dto.request.LoginRequest;
import org.deepstack.ai.app.model.dto.response.LoginResponse;
import org.deepstack.ai.app.service.AuthService;
import org.deepstack.ai.kernel.auth.Anonymous;
import org.deepstack.ai.kernel.auth.BearerTokens;
import org.deepstack.ai.kernel.auth.LoginUser;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.deepstack.ai.kernel.model.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证 API：登录、当前用户、登出。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户名密码登录，返回 JWT，并将会话写入 Redis。
     */
    @Anonymous
    @PostMapping("/login")
    public Response<LoginResponse> login(@RequestBody LoginRequest req) {
        return Response.success(authService.login(req));
    }

    /**
     * 获取当前登录用户（{@code @LoginUser} 从 Redis 会话解析）。
     */
    @GetMapping("/me")
    public Response<Map<String, Object>> me(@LoginUser AppUserInfo loginUser) {
        log.info("查询当前用户: userId={}", loginUser != null ? loginUser.getUserId() : null);
        return Response.success(Map.of(
                "userId", loginUser.getUserId(),
                "username", loginUser.getUsername() != null ? loginUser.getUsername() : "",
                "displayName", loginUser.getNickname() != null ? loginUser.getNickname() : "",
                "isAdmin", loginUser.getIsAdmin() != null ? loginUser.getIsAdmin() : 0
        ));
    }

    /**
     * 登出：删除 Redis 会话。
     */
    @Anonymous
    @PostMapping("/logout")
    public Response<Void> logout(HttpServletRequest request) {
        String token = BearerTokens.resolve(request);
        authService.logout(token);
        log.info("用户登出");
        return Response.success();
    }
}
