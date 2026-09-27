package org.deepstack.ai.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 图谱边（有向）。
 */
public final class GraphEdge {

    /** 起点节点 key */
    private final String fromKey;

    /** 终点节点 key */
    private final String toKey;

    /** 关系类型，如 MENTIONS / RELATED_TO */
    private final String type;

    private final Map<String, Object> properties;

    public GraphEdge(String fromKey, String toKey, String type, Map<String, Object> properties) {
        this.fromKey = Objects.requireNonNull(fromKey, "fromKey");
        this.toKey = Objects.requireNonNull(toKey, "toKey");
        this.type = Objects.requireNonNull(type, "type");
        this.properties = properties == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /** 返回 FromKey。 */
    public String getFromKey() {
        return fromKey;
    }

    /** 返回 ToKey。 */
    public String getToKey() {
        return toKey;
    }

    /** 返回 Type。 */
    public String getType() {
        return type;
    }

    /** 返回 Properties。 */
    public Map<String, Object> getProperties() {
        return properties;
    }
}
