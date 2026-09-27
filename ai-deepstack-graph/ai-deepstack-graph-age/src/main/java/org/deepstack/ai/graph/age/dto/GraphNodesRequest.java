package org.deepstack.ai.graph.age.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class GraphNodesRequest {
    private String scope;
    private List<NodeItem> nodes = new ArrayList<>();

    @Data
    public static class NodeItem {
        private String key;
        private String label;
        private Map<String, Object> properties;
    }
}
