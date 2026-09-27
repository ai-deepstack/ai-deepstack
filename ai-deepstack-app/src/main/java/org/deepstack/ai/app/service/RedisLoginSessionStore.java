package org.deepstack.ai.app.service;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.kernel.auth.LoginSessionStore;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 基于 Redis 的登录会话：key = {@code deepstack:login:session:{md5(token)}}。
 */
@Slf4j
@Service
public class RedisLoginSessionStore implements LoginSessionStore {

    private static final String KEY_PREFIX = "deepstack:login:session:";

    private final StringRedisTemplate redis;
    private final Duration defaultTtl;

    public RedisLoginSessionStore(
            StringRedisTemplate redis,
            @Value("${deepstack.auth.jwt-expire-hours:72}") long expireHours) {
        this.redis = redis;
        this.defaultTtl = Duration.ofHours(Math.max(1L, expireHours));
    }

    /** 写入会话；token 为空则忽略。 */
    @Override
    public void save(String token, AppUserInfo user, Duration ttl) {
        if (token == null || token.isBlank() || user == null) {
            return;
        }
        Duration effective = ttl == null || ttl.isZero() || ttl.isNegative() ? defaultTtl : ttl;
        String key = cacheKey(token);
        String json = JSON.toJSONString(user);
        redis.opsForValue().set(key, json, effective);
        log.info("登录会话已写入 Redis: userId={}, ttlHours={}", user.getUserId(), effective.toHours());
    }

    /** get。 */
    @Override
    public AppUserInfo get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String key = cacheKey(token);
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        AppUserInfo user = JSON.parseObject(json, AppUserInfo.class);
        // 滑动续期（与 apw 类似）
        redis.expire(key, defaultTtl);
        return user;
    }

    /** 删除会话。 */
    @Override
    public void remove(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redis.delete(cacheKey(token));
        log.info("登录会话已从 Redis 删除");
    }

    /** 判断 token 是否仍有效。 */
    @Override
    public boolean exists(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(cacheKey(token)));
    }

    /** 拼 Redis 缓存键。 */
    private static String cacheKey(String token) {
        String hash = DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + hash;
    }
}
