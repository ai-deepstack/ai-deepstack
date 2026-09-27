package org.deepstack.ai.app.spi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.app.mapper.AiUserMapper;
import org.deepstack.ai.app.model.entity.AiUser;
import org.deepstack.ai.kernel.auth.AppUserProfileLoader;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.springframework.stereotype.Component;

/**
 * 从 DB 加载登录用户档案（覆盖 kernel demo 实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbAppUserProfileLoader implements AppUserProfileLoader {

    private final AiUserMapper aiUserMapper;

    /**
     * 按 userId 加载用户；不存在或禁用时抛业务异常。
     */
    @Override
    public AppUserInfo load(Long userId) {
        log.info("加载用户档案: userId={}", userId);
        AiUser user = aiUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NEED_LOGIN);
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            log.warn("用户已禁用: userId={}", userId);
            BusinessException.of(CommonErrorCode.UNAUTHORIZED);
        }
        AppUserInfo info = new AppUserInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        info.setIsAdmin(user.getIsAdmin() != null ? user.getIsAdmin() : 0);
        return info;
    }
}
