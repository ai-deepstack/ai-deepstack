package org.deepstack.ai.graph.age;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识图谱配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "deepstack.graph")
public class DeepstackGraphProperties {

    /**
     * yml 兜底默认（迁移用）；运行时业务总开关以 sys_config {@code graph.enabled}（0/1）为准。
     */
    private boolean enabled = false;

    /** 实现：age（后续可 neo4j 等） */
    private String provider = "age";

    private final Age age = new Age();

    @Getter
    @Setter
    public static class Age {
        /**
         * 默认图名；isolation=PROPERTY 时所有 scope 共用此图。
         */
        private String defaultGraph = "deepstack_kg";

        /**
         * GRAPH：每个 scope 一张 AGE 图；PROPERTY：单图 + 节点属性 scope。
         */
        private String isolation = "PROPERTY";
    }
}
