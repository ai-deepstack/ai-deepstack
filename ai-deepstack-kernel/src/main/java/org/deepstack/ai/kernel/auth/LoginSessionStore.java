package org.deepstack.ai.kernel.auth;

import org.deepstack.ai.kernel.model.AppUserInfo;

import java.time.Duration;

/**
 * 登录会话存储（实现一般为 Redis）：以 token 为键保存 {@link AppUserInfo}。
 */
public interface LoginSessionStore {

    /**
     * 登录成功后写入会话。
     *
     * @param token 客户端持有的访问令牌
     * @param user  用户档案
     * @param ttl   过期时间
     */
    void save(String token, AppUserInfo user, Duration ttl);

    /**
     * 按 token 读取会话；不存在或已过期返回 null。
     * <p>实现可选择滑动续期。</p>
     *
     * @param token 访问令牌
     * @return 用户档案或 null
     */
    AppUserInfo get(String token);

    /**
     * 登出或强制失效时删除会话。
     *
     * @param token 访问令牌
     */
    void remove(String token);

    /**
     * 会话是否存在。
     *
     * @param token 访问令牌
     * @return true 表示已登录且未过期
     */
    default boolean exists(String token) {
        return get(token) != null;
    }
}
