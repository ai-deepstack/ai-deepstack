package org.deepstack.ai.runtime;

import org.deepstack.ai.engine.tool.AgentTool;
import org.deepstack.ai.engine.WorkflowStreamEvent;
import org.deepstack.ai.memory.MemoryPort;
import org.deepstack.ai.runtime.spi.CardPort;
import org.deepstack.ai.runtime.spi.KnowledgePort;
import org.deepstack.ai.runtime.spi.ModelPort;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import reactor.core.publisher.Sinks;

/**
 * 工作流节点 Port 聚合器：仅依赖 SPI，不承载请求态（请求态见 {@link RunContext}）。
 * <p>
 * 图节点通过本类获取模型/工具/知识/卡片/记忆能力，以及当前 RunContext 上的流式 sink 与用户身份。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentNodeContext {

    private final ModelPort modelPort;
    private final ToolPort toolPort;
    private final KnowledgePort knowledgePort;
    private final CardPort cardPort;
    private final MemoryPort memoryPort;
    private final ObjectProvider<ToolDisclosureSessionFactory> toolDisclosureSessionFactory;

    /**
     * 按模型编码解析 ChatModel。
     *
     * @param modelCode 模型编码（ai_model）
     * @return 对应 ChatModel
     * @throws IllegalArgumentException modelCode 为空
     */
    public ChatModel getChatModel(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("modelCode 不能为空");
        }
        return modelPort.getChatModel(modelCode);
    }

    /**
     * 按模型编码解析上游 API 模型名（如 gpt-4o）。
     *
     * @param modelCode 模型编码（ai_model）
     * @return 供应商侧 api_model_name
     * @throws IllegalArgumentException modelCode 为空
     */
    public String getApiModelName(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("modelCode 不能为空");
        }
        return modelPort.getApiModelName(modelCode);
    }

    /**
     * 获取知识检索端口。
     *
     * @return {@link KnowledgePort}
     */
    public KnowledgePort getKnowledgePort() {
        return knowledgePort;
    }

    /**
     * 按工具编码获取可执行 {@link AgentTool}。
     *
     * @param toolCode 工具编码
     * @return 对应 AgentTool；不存在时由 ToolPort 约定返回值
     */
    public AgentTool getTool(String toolCode) {
        return toolPort.getAgentTool(toolCode);
    }

    /**
     * 获取工具端口（含 AgentTool 与底层回调能力）。
     *
     * @return {@link ToolPort}
     */
    public ToolPort getToolPort() {
        return toolPort;
    }

    /**
     * 获取工具披露会话工厂（按允许集打开 DisclosureSession）。
     *
     * @return 工厂实例；未装配时返回 null
     */
    public ToolDisclosureSessionFactory getToolDisclosureSessionFactory() {
        return toolDisclosureSessionFactory.getIfAvailable();
    }

    /**
     * 获取卡片端口（产出/持久化交互卡片）。
     *
     * @return {@link CardPort}
     */
    public CardPort getCardPort() {
        return cardPort;
    }

    /**
     * 获取记忆端口（长期记忆读写）。
     *
     * @return {@link MemoryPort}
     */
    public MemoryPort getMemoryPort() {
        return memoryPort;
    }

    /**
     * 当前运行的流式事件 sink（来自 {@link RunContext}）。
     *
     * @return 流式 sink；无当前 RunContext 时返回 null
     */
    public Sinks.Many<WorkflowStreamEvent> getStreamSink() {
        RunContext ctx = RunContext.current();
        return ctx != null ? ctx.streamSink() : null;
    }

    /**
     * 当前运行的卡片发射器（来自 {@link RunContext}）。
     *
     * @return 卡片回调；无当前 RunContext 时返回 null
     */
    public Consumer<Object> getCardEmitter() {
        RunContext ctx = RunContext.current();
        return ctx != null ? ctx.cardEmitter() : null;
    }

    /**
     * 当前运行的用户 ID。
     *
     * @return userId；无当前 RunContext 时返回 null
     */
    public String currentUserId() {
        RunContext ctx = RunContext.current();
        return ctx != null ? ctx.userId() : null;
    }

    /**
     * 当前运行的会话 ID。
     *
     * @return conversationId；无当前 RunContext 时返回 null
     */
    public String currentConversationId() {
        RunContext ctx = RunContext.current();
        return ctx != null ? ctx.conversationId() : null;
    }

    /**
     * 当前运行的智能体编码。
     *
     * @return agentCode；无当前 RunContext 时返回 null
     */
    public String currentAgentCode() {
        RunContext ctx = RunContext.current();
        return ctx != null ? ctx.agentCode() : null;
    }
}

