package org.deepstack.ai.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.app.mapper.AiUserMapper;
import org.deepstack.ai.app.model.dto.request.LoginRequest;
import org.deepstack.ai.app.model.dto.response.LoginResponse;
import org.deepstack.ai.app.model.entity.AiUser;
import org.deepstack.ai.app.service.AuthService;
import org.deepstack.ai.app.service.JwtService;
import org.deepstack.ai.kernel.auth.LoginSessionStore;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 应用认证服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AiUserMapper aiUserMapper;
    private final JwtService jwtService;
    private final LoginSessionStore loginSessionStore;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${deepstack.auth.jwt-expire-hours:72}")
    private long expireHours;

    /** 校验账号并建立登录会话。 */
    @Override
    public LoginResponse login(LoginRequest req) {
        log.info("用户登录: username={}", req != null ? req.getUsername() : null);
        if (req == null || req.getUsername() == null || req.getUsername().isBlank()
                || req.getPassword() == null) {
            BusinessException.of(CommonErrorCode.MISSING_PARAM);
        }
        AiUser user = aiUserMapper.selectOne(new LambdaQueryWrapper<AiUser>()
                .eq(AiUser::getUsername, req.getUsername().trim()));
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NEED_LOGIN.getCode(), "用户名或密码错误");
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            BusinessException.of(CommonErrorCode.UNAUTHORIZED.getCode(), "账号已禁用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            BusinessException.of(CommonErrorCode.NEED_LOGIN.getCode(), "用户名或密码错误");
        }

        String token = jwtService.createToken(user.getId(), user.getUsername());
        AppUserInfo sessionUser = new AppUserInfo();
        sessionUser.setUserId(user.getId());
        sessionUser.setUsername(user.getUsername());
        sessionUser.setNickname(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        sessionUser.setIsAdmin(user.getIsAdmin() != null ? user.getIsAdmin() : 0);
        loginSessionStore.save(token, sessionUser, Duration.ofHours(Math.max(1L, expireHours)));

        LoginResponse body = new LoginResponse();
        body.setToken(token);
        body.setUserId(user.getId());
        body.setDisplayName(sessionUser.getNickname());
        body.setIsAdmin(sessionUser.getIsAdmin());
        return body;
    }

    /** 注销当前 token 会话。 */
    @Override
    public void logout(String token) {
        log.info("用户登出");
        loginSessionStore.remove(token);
    }
}
