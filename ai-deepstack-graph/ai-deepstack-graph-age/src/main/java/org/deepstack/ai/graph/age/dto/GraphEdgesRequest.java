package org.deepstack.ai.graph.age.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class GraphEdgesRequest {
    private String scope;
    private List<EdgeItem> edges = new ArrayList<>();

    @Data
    public static class EdgeItem {
        private String fromKey;
        private String toKey;
        private String type;
        private Map<String, Object> properties;
    }
}
