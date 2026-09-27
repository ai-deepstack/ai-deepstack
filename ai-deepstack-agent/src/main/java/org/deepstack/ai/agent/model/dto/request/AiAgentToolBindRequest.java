package org.deepstack.ai.agent.model.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 智能体-工具绑定请求
 * <p>
 * 传入工具 id 列表，后端先删后插，保持智能体与工具的多对多绑定关系。
 * </p>
 *
 */
@Data
public class AiAgentToolBindRequest implements Serializable {

    /**
     * 智能体 id
     */
    private Long agentId;

    /**
     * 工具 id 列表（完整替换，传空列表表示清空所有绑定）
     */
    private List<Long> toolIds;
}
