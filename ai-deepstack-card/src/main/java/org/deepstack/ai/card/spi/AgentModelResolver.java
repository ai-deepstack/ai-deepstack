package org.deepstack.ai.card.spi;

/**
 * 按 Agent 编码解析默认模型编码，避免 card 模块依赖 agent。
 * 由 agent 模块实现。
 */
public interface AgentModelResolver {

    /**
     * 按 Agent 编码解析默认模型编码。
     *
     * @param agentCode Agent 编码
     * @return 模型编码；未知时 null
     */
    String resolveModelCodeByAgentCode(String agentCode);
}
