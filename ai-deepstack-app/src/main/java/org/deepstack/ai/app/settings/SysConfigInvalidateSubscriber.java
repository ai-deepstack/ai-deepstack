package org.deepstack.ai.app.settings;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 订阅 {@code deepstack:sys_config:invalidate}，收到 key 后 DEL 本地 Redis 缓存；{@code *} 清全部已知 key。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysConfigInvalidateSubscriber implements MessageListener {

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider;

    @PostConstruct
    void subscribe() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        RedisMessageListenerContainer container = listenerContainerProvider.getIfAvailable();
        if (redis == null || container == null) {
            log.info("sys_config invalidate subscribe skipped: redis or listener container unavailable");
            return;
        }
        container.addMessageListener(this, new ChannelTopic(SysConfigPortImpl.INVALIDATE_CHANNEL));
        log.info("sys_config invalidate subscribed: channel={}", SysConfigPortImpl.INVALIDATE_CHANNEL);
    }

    /** 收到配置失效广播后刷新本地缓存。 */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || message == null || message.getBody() == null) {
            return;
        }
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        if (!StringUtils.hasText(body)) {
            return;
        }
        try {
            if ("*".equals(body)) {
                for (String key : SysConfigFallback.CODED_DEFAULTS.keySet()) {
                    redis.delete(SysConfigPortImpl.REDIS_KEY_PREFIX + key);
                }
                log.debug("sys_config invalidate: cleared all known keys");
            } else {
                redis.delete(SysConfigPortImpl.REDIS_KEY_PREFIX + body);
                log.debug("sys_config invalidate: deleted key={}", body);
            }
        } catch (Exception e) {
            log.warn("sys_config invalidate handle failed: body={}, err={}", body, e.getMessage());
        }
    }
}
