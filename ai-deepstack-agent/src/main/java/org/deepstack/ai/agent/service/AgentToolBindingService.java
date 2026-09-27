package org.deepstack.ai.agent.service;



import org.deepstack.ai.agent.model.dto.request.AiAgentToolBindRequest;

import org.deepstack.ai.agent.model.dto.response.AiAgentToolResponse;



import java.util.List;



/**

 * 智能体-工具绑定（编排域）。

 */

public interface AgentToolBindingService {



    /**

     * 查询智能体已绑定的工具列表（含工具元数据，按 priority 升序）。

     *

     * @param agentId 智能体主键

     * @return 绑定响应列表；无绑定时返回空列表

     */

    List<AiAgentToolResponse> listToolsByAgent(Long agentId);



    /**

     * 全量重绑智能体与工具（先删后插，列表顺序即优先级），并驱逐工具端口缓存。

     *

     * @param req 绑定请求（agentId + toolIds）

     */

    void bindTools(AiAgentToolBindRequest req);



    /**

     * 查询智能体启用中的 toolCode（按 priority），供图运行注入。

     *

     * @param agentId 智能体主键

     * @return toolCode 列表；无启用绑定时返回空列表

     */

    List<String> listEnabledToolCodes(Long agentId);

}


