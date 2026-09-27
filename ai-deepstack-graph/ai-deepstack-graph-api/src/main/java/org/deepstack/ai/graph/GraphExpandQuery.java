package org.deepstack.ai.graph;

import java.util.List;
import java.util.Objects;

/**
 * 邻居/多跳扩展查询。
 */
public final class GraphExpandQuery {

    /** 隔离范围，通常为知识库编码或 kb:{id} */
    private final String scope;

    /** 起点节点 key */
    private final String startKey;

    /** 最大跳数（含） */
    private final int hops;

    /** 返回节点上限 */
    private final int limit;

    /** 限制边类型；空表示不限 */
    private final List<String> edgeTypes;

    public GraphExpandQuery(String scope, String startKey, int hops, int limit, List<String> edgeTypes) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.startKey = Objects.requireNonNull(startKey, "startKey");
        this.hops = Math.max(1, hops);
        this.limit = Math.max(1, limit);
        this.edgeTypes = edgeTypes == null ? List.of() : List.copyOf(edgeTypes);
    }

    /** 返回 Scope。 */
    public String getScope() {
        return scope;
    }

    /** 返回 StartKey。 */
    public String getStartKey() {
        return startKey;
    }

    /** 返回 Hops。 */
    public int getHops() {
        return hops;
    }

    /** 返回 Limit。 */
    public int getLimit() {
        return limit;
    }

    /** 返回 EdgeTypes。 */
    public List<String> getEdgeTypes() {
        return edgeTypes;
    }
}
