package org.deepstack.ai.runtime.checkpoint;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.deepstack.ai.engine.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Checkpoint 装配：引入即建 Bean；业务开关走 sys_config {@code checkpoint.enabled}（0/1）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DeepstackCheckpointProperties.class)
public class CheckpointConfiguration {

    /** 装配图检查点存储器。 */
    @Bean
    @ConditionalOnMissingBean(BaseCheckpointSaver.class)
    public BaseCheckpointSaver checkpointSaver(DeepstackCheckpointProperties props, DataSource dataSource)
            throws SQLException {
        if ("postgres".equalsIgnoreCase(props.getProvider())) {
            log.info("CheckpointSaver: PostgresSaver (createTables={})", props.isCreateTables());
            return PostgresSaver.builder()
                    .datasource(dataSource)
                    .stateSerializer(new ObjectStreamStateSerializer<>(WorkflowState::new))
                    .createTables(props.isCreateTables())
                    .build();
        }
        log.info("CheckpointSaver: MemorySaver (provider={})", props.getProvider());
        return new MemorySaver();
    }
}
