package org.deepstack.ai.knowledge.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库图谱摘要：就绪状态、节点/边约数、最近写图时间。
 */
@Data
public class KnowledgeGraphSummaryResponse implements Serializable {

    private String scope;

    /** 平台 graph.enabled */
    private boolean graphEnabled;

    /** GraphStore 已装配且 ready */
    private boolean storeReady;

    /** 库级 enable_graph */
    private boolean enableGraph;

    private long nodeCount;

    private long edgeCount;

    private long documentCount;

    private long chunkCount;

    private long entityCount;

    /** 该库最近一次写图成功的文档更新时间 */
    private LocalDateTime lastGraphTime;

    /** 未就绪或查询失败时的说明 */
    private String message;
}
