package org.deepstack.ai.kernel.auth;

import org.deepstack.ai.kernel.model.AppUserInfo;

/**
 * 用户资料加载 SPI。开源默认提供演示实现；产品侧可替换。
 */
public interface AppUserProfileLoader {
    AppUserInfo load(Long userId);
}
