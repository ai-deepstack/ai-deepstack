package org.deepstack.ai.graph.age;

import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.settings.SysConfigPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Apache AGE 图谱实现装配：{@code provider=age} 时引入即建 Bean；业务总开关走 sys_config graph.enabled。
 */
@Configuration
@EnableConfigurationProperties(DeepstackGraphProperties.class)
@ConditionalOnProperty(prefix = "deepstack.graph", name = "provider", havingValue = "age", matchIfMissing = true)
public class AgeGraphConfiguration {

    /** 装配 AGE 图存储。 */
    @Bean
    @ConditionalOnMissingBean(GraphStore.class)
    public GraphStore ageGraphStore(JdbcTemplate jdbcTemplate,
                                    DeepstackGraphProperties properties,
                                    SysConfigPort sysConfigPort) {
        return new AgeGraphStore(jdbcTemplate, properties, sysConfigPort);
    }
}
