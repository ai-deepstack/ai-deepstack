package org.deepstack.ai.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 图谱节点（属性图模型）。
 */
public final class GraphNode {

    /** 业务主键（同 scope 内唯一），如 entity:退货政策 */
    private final String key;

    /** 节点标签，如 Entity / Document / Chunk */
    private final String label;

    /** 扩展属性（实现方可附加 scope、documentId 等） */
    private final Map<String, Object> properties;

    public GraphNode(String key, String label, Map<String, Object> properties) {
        this.key = Objects.requireNonNull(key, "key");
        this.label = Objects.requireNonNull(label, "label");
        this.properties = properties == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /** 返回 Key。 */
    public String getKey() {
        return key;
    }

    /** 返回 Label。 */
    public String getLabel() {
        return label;
    }

    /** 返回 Properties。 */
    public Map<String, Object> getProperties() {
        return properties;
    }
}
