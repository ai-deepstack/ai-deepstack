package org.deepstack.ai.agent.web;

import org.deepstack.ai.agent.model.dto.request.AiAgentToolBindRequest;
import org.deepstack.ai.agent.model.dto.response.AiAgentToolResponse;
import org.deepstack.ai.agent.service.AgentToolBindingService;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能体工具绑定 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolBindingService agentToolBindingService;

    /**
     * 列出智能体已绑定的工具（按 priority）。
     *
     * @param id 智能体主键
     * @return 绑定列表
     */
    @GetMapping("/{id}/tools")
    public Response<List<AiAgentToolResponse>> listTools(@PathVariable("id") Long id) {
        log.info("API listTools: agentId={}", id);
        return Response.success(agentToolBindingService.listToolsByAgent(id));
    }

    /**
     * 全量重绑智能体工具（列表顺序即优先级）。
     *
     * @param id      智能体主键
     * @param toolIds 工具主键列表；null 视为清空
     */
    @PutMapping("/{id}/tools")
    public Response<Void> bindTools(@PathVariable("id") Long id, @RequestBody List<Long> toolIds) {
        log.info("API bindTools: agentId={}, toolIds={}", id, toolIds);
        AiAgentToolBindRequest req = new AiAgentToolBindRequest();
        req.setAgentId(id);
        req.setToolIds(toolIds != null ? toolIds : List.of());
        agentToolBindingService.bindTools(req);
        return Response.success();
    }
}
