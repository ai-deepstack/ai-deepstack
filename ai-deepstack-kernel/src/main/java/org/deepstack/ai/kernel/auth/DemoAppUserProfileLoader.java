package org.deepstack.ai.kernel.auth;

import org.deepstack.ai.kernel.model.AppUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 演示用用户档案加载器（无其它 {@link AppUserProfileLoader} 时启用）。
 */
@Slf4j
@Component
@ConditionalOnMissingBean(AppUserProfileLoader.class)
public class DemoAppUserProfileLoader implements AppUserProfileLoader {

    /**
     * 返回固定 demo 用户；userId 为空时默认为 1。
     */
    @Override
    public AppUserInfo load(Long userId) {
        Long resolved = userId != null ? userId : 1L;
        log.info("Demo profile load: userId={}", resolved);
        AppUserInfo info = new AppUserInfo();
        info.setUserId(resolved);
        info.setUsername("demo");
        info.setNickname("Demo User");
        return info;
    }
}
