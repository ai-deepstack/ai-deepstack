package org.deepstack.ai.memory.pg;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "deepstack.memory")
public class DeepstackMemoryProperties {

    /**
     * yml 兜底默认；运行时总开关以 sys_config {@code memory.enabled}（0/1）为准。
     */
    private boolean enabled = false;

    /** 实现：pg（PG + 可选 AGE） */
    private String provider = "pg";

    /**
     * yml 兜底；运行时以 sys_config {@code memory.graph-by-default}（0/1）为准。
     */
    private boolean graphByDefault = false;
}
