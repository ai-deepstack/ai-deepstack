package org.deepstack.ai.agent.spi;

import org.deepstack.ai.agent.graph.AgentGraphSpecs;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.agent.observability.AgentRunService;
import org.deepstack.ai.agent.service.AiAgentService;
import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.engine.GraphSpec;
import org.deepstack.ai.engine.TraceConfig;
import org.deepstack.ai.runtime.GraphRuntime;
import org.deepstack.ai.runtime.spi.HitlPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * HITL 恢复端口适配：按 agentCode 加载图定义后调用 {@link GraphRuntime#resume}，
 * 并经 {@link AgentRunService#runHitlResume} 回写挂起等待时长与终态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HitlPortAdapter implements HitlPort {

    private final AiAgentService aiAgentService;
    private final GraphRuntime graphRuntime;
    private final AgentRunService agentRunService;

    /** 恢复 HITL 挂起的图运行。 */
    @Override
    public GraphRunResponse resume(String agentCode, String threadId, Map<String, Object> stateUpdates,
                                   GraphRunRequest baseRequest) {
        if (!StringUtils.hasText(agentCode)) {
            throw new IllegalArgumentException("agentCode 不能为空");
        }
        if (!StringUtils.hasText(threadId)) {
            throw new IllegalArgumentException("threadId 不能为空");
        }

        AiAgent agent = aiAgentService.getByAgentCode(agentCode);
        if (agent == null || !StringUtils.hasText(agent.getGraphDefinition())) {
            throw new IllegalStateException("智能体图定义不存在: " + agentCode);
        }

        GraphSpec workflow = AgentGraphSpecs.from(agent);
        GraphRunRequest req = baseRequest != null ? baseRequest : new GraphRunRequest();
        if (!StringUtils.hasText(req.getAgentCode())) {
            req.setAgentCode(agentCode);
        }

        log.info("HitlPort.resume: agentCode={}, threadId={}, updates={}",
                agentCode, threadId, stateUpdates != null ? stateUpdates.keySet() : null);

        try {
            return agentRunService.runHitlResume(threadId, agentCode,
                    () -> graphRuntime.resume(workflow, threadId, stateUpdates, req, new TraceConfig()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("HITL resume failed: " + e.getMessage(), e);
        }
    }
}
