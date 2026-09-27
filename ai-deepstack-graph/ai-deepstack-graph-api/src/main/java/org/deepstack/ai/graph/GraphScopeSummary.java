package org.deepstack.ai.graph;

/**
 * 某个 scope 的图谱规模摘要（约数，供控制台展示）。
 */
public final class GraphScopeSummary {

    private final long nodeCount;
    private final long edgeCount;
    private final long documentCount;
    private final long chunkCount;
    private final long entityCount;

    public GraphScopeSummary(long nodeCount, long edgeCount,
                             long documentCount, long chunkCount, long entityCount) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.documentCount = documentCount;
        this.chunkCount = chunkCount;
        this.entityCount = entityCount;
    }

    /** 返回空结果占位。 */
    public static GraphScopeSummary empty() {
        return new GraphScopeSummary(0, 0, 0, 0, 0);
    }

    /** 返回 NodeCount。 */
    public long getNodeCount() {
        return nodeCount;
    }

    /** 返回 EdgeCount。 */
    public long getEdgeCount() {
        return edgeCount;
    }

    /** 返回 DocumentCount。 */
    public long getDocumentCount() {
        return documentCount;
    }

    /** 返回 ChunkCount。 */
    public long getChunkCount() {
        return chunkCount;
    }

    /** 返回 EntityCount。 */
    public long getEntityCount() {
        return entityCount;
    }
}
