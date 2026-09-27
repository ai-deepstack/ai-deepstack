package org.deepstack.ai.knowledge.model.dto.response;

import org.deepstack.ai.graph.GraphEdge;
import org.deepstack.ai.graph.GraphNode;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库图谱只读视图（控制台）。
 */
@Data
public class KnowledgeGraphViewResponse implements Serializable {

    /** 隔离 scope，固定为 kb:{baseCode} */
    private String scope;

    /** 扩展起点 key（doc_* 或 chk_*） */
    private String startKey;

    private List<GraphNode> nodes = new ArrayList<>();

    private List<GraphEdge> edges = new ArrayList<>();

    /** 无可展示数据或未就绪时的说明 */
    private String message;
}
