package org.deepstack.ai.memory.pg;

import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.memory.LongTermMemoryStore;
import org.deepstack.ai.memory.MemoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * PG + 可选 AGE 长期记忆装配：provider=pg 时引入即建 Bean；业务总开关走 sys_config memory.enabled。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DeepstackMemoryProperties.class)
@ConditionalOnProperty(prefix = "deepstack.memory", name = "provider", havingValue = "pg", matchIfMissing = true)
public class MemoryPgConfiguration {

    /**
     * 记忆图扩边专用池，与知识库召回池隔离。
     */
    @Bean("memoryRecallExecutor")
    @ConditionalOnMissingBean(name = "memoryRecallExecutor")
    public Executor memoryRecallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("mem-recall-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("memoryRecallExecutor 已初始化: core=2, max=8, queue=32");
        return executor;
    }

    /** 装配 PG 混合长期记忆存储。 */
    @Bean
    @ConditionalOnMissingBean(LongTermMemoryStore.class)
    public LongTermMemoryStore pgHybridLongTermMemoryStore(
            AiLongTermMemoryMapper mapper,
            ObjectProvider<GraphStore> graphStoreProvider,
            SysConfigPort sysConfigPort,
            @Qualifier("memoryRecallExecutor") Executor memoryRecallExecutor) {
        return new PgHybridLongTermMemoryStore(
                mapper, graphStoreProvider, sysConfigPort, memoryRecallExecutor);
    }

    /** 装配记忆端口适配器。 */
    @Bean
    @ConditionalOnMissingBean(MemoryPort.class)
    public MemoryPort memoryPort(LongTermMemoryStore store, SysConfigPort sysConfigPort) {
        return new MemoryPortAdapter(store, sysConfigPort);
    }
}
