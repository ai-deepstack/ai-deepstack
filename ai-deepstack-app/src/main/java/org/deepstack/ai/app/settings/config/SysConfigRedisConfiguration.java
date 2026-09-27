package org.deepstack.ai.app.settings.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * sys_config 失效订阅所需的 Redis 消息监听容器。
 */
@Configuration
public class SysConfigRedisConfiguration {

    /** 装配 Redis 配置失效监听容器。 */
    @Bean
    @ConditionalOnBean({RedisConnectionFactory.class, StringRedisTemplate.class})
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
