package org.deepstack.ai.graph;

/**
 * 当前图谱实现元信息。
 */
public final class GraphProviderInfo {

    private final String provider;
    private final boolean ready;
    private final String detail;

    public GraphProviderInfo(String provider, boolean ready, String detail) {
        this.provider = provider;
        this.ready = ready;
        this.detail = detail;
    }

    /** 返回 Provider。 */
    public String getProvider() {
        return provider;
    }

    /** 是否 Ready。 */
    public boolean isReady() {
        return ready;
    }

    /** 返回 Detail。 */
    public String getDetail() {
        return detail;
    }
}
