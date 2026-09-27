package org.deepstack.ai.runtime.checkpoint;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图执行 Checkpoint / HITL 配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "deepstack.checkpoint")
public class DeepstackCheckpointProperties {

    /** yml 兜底；运行时以 sys_config {@code checkpoint.enabled}（0/1）为准 */
    private boolean enabled = true;

    /** memory = 进程内 MemorySaver；postgres = LangGraph4j PostgresSaver */
    private String provider = "postgres";

    /** postgres 时是否自动建表（LG4JCheckpoint / LG4JThread） */
    private boolean createTables = true;
}
