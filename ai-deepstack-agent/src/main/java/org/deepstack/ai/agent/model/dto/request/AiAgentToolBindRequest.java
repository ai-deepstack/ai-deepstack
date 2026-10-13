package org.deepstack.ai.agent.model.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent-工具绑定请求
 * <p>
 * 传入工具 id 列表，后端先删后插，保持 Agent 与工具的多对多绑定关系。
 * </p>
 *
 */
@Data
public class AiAgentToolBindRequest implements Serializable {

    /**
     * Agent id
     */
    private Long agentId;

    /**
     * 工具 id 列表（完整替换，传空列表表示清空所有绑定）
     */
    private List<Long> toolIds;
}
