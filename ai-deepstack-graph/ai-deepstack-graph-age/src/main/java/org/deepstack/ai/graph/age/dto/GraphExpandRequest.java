package org.deepstack.ai.graph.age.dto;

import lombok.Data;

import java.util.List;

@Data
public class GraphExpandRequest {
    private String scope;
    private String startKey;
    private Integer hops = 1;
    private Integer limit = 20;
    private List<String> edgeTypes;
}
