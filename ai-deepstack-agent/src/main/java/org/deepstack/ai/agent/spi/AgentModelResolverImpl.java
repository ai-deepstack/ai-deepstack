package org.deepstack.ai.agent.spi;

import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.agent.service.AiAgentService;
import org.deepstack.ai.card.spi.AgentModelResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * card 模块解析模型编码的 SPI 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModelResolverImpl implements AgentModelResolver {

    private final AiAgentService aiAgentService;

    /** 按智能体编码解析绑定的模型编码。 */
    @Override
    public String resolveModelCodeByAgentCode(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            log.warn("resolveModelCodeByAgentCode: blank agentCode");
            return null;
        }
        AiAgent agent = aiAgentService.getByAgentCode(agentCode);
        if (agent == null || !StringUtils.hasText(agent.getModelCode())) {
            log.warn("resolveModelCodeByAgentCode: no model, agentCode={}", agentCode);
            return null;
        }
        log.debug("resolveModelCodeByAgentCode: agentCode={}, modelCode={}", agentCode, agent.getModelCode());
        return agent.getModelCode();
    }
}
