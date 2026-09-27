package org.deepstack.ai.runtime.config;

import org.deepstack.ai.engine.tool.AgentTool;
import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;
import org.deepstack.ai.memory.MemoryPort;
import org.deepstack.ai.runtime.spi.CardPort;
import org.deepstack.ai.runtime.spi.HitlPort;
import org.deepstack.ai.runtime.spi.KnowledgePort;
import org.deepstack.ai.runtime.spi.ModelPort;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.runtime.spi.tool.InMemoryToolDescriptorIndex;
import org.deepstack.ai.runtime.spi.tool.PassthroughToolDisclosureSessionFactory;
import org.deepstack.ai.runtime.spi.tool.ToolDescriptorIndex;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSessionFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 缺省空端口：未在应用层装配 Adapter 时仍可启动 runtime。
 */
@Configuration
public class RuntimeSpiAutoConfiguration {

    /** 未装配模型端口时的占位实现。 */
    @Bean
    @ConditionalOnMissingBean(ModelPort.class)
    public ModelPort missingModelPort() {
        return new ModelPort() {
            /** 返回 ChatModel。 */
            @Override
            public ChatModel getChatModel(String modelCode) {
                throw new IllegalStateException("ModelPort not configured: wire adapter in application");
            }

            /** 返回 ApiModelName。 */
            @Override
            public String getApiModelName(String modelCode) {
                throw new IllegalStateException("ModelPort not configured: wire adapter in application");
            }
        };
    }

    /** 未装配工具端口时的占位实现。 */
    @Bean
    @ConditionalOnMissingBean(ToolPort.class)
    public ToolPort missingToolPort() {
        return new ToolPort() {
            /** 按工具编码解析回调，占位实现返回空。 */
            @Override
            public List<ToolCallback> resolveToolCallbacksByCodes(List<String> toolCodes) {
                return List.of();
            }

            /** 返回 AgentTool。 */
            @Override
            public AgentTool getAgentTool(String toolCode) {
                return null;
            }

            /** 列出已注册工具，占位实现返回空。 */
            @Override
            public List<AgentTool> listAgentTools() {
                return List.of();
            }
        };
    }

    /** 未装配知识端口时的占位实现。 */
    @Bean
    @ConditionalOnMissingBean(KnowledgePort.class)
    public KnowledgePort missingKnowledgePort() {
        return new KnowledgePort() {
            /** 检索单个知识库，占位实现返回空。 */
            @Override
            public List<String> retrieve(String baseCode, String query, int topK, double similarityThreshold) {
                return List.of();
            }

            /** 检索多个知识库，占位实现返回空。 */
            @Override
            public List<String> retrieveAll(List<String> baseCodes, String query,
                                            int topK, double similarityThreshold) {
                return List.of();
            }
        };
    }

    /** 未装配卡片端口时的占位实现。 */
    @Bean
    @ConditionalOnMissingBean(CardPort.class)
    public CardPort missingCardPort() {
        return new CardPort() {
        };
    }

    /** 未装配记忆端口时的占位实现。 */
    @Bean
    @ConditionalOnMissingBean(MemoryPort.class)
    public MemoryPort missingMemoryPort() {
        return new MemoryPort() {
            /** 当前能力是否可用。 */
            @Override
            public boolean available() {
                return false;
            }

            /** 召回并格式化记忆上下文。 */
            @Override
            public String recallContext(String userId, String agentCode, String query, int limit, boolean enableGraph) {
                return "";
            }

            /** 写入一条长期记忆。 */
            @Override
            public void remember(String userId, String agentCode, String content, String category,
                                 boolean enableGraph, List<String> entities, String conversationId) {
                // no-op
            }
        };
    }

    /** 未装配 HITL 端口时的占位实现。 */
    @Bean
    @ConditionalOnMissingBean(HitlPort.class)
    public HitlPort missingHitlPort() {
        return new HitlPort() {
            /** 当前能力是否可用。 */
            @Override
            public boolean available() {
                return false;
            }

            /** 恢复 HITL 挂起的图运行。 */
            @Override
            public GraphRunResponse resume(String agentCode, String threadId, Map<String, Object> stateUpdates,
                                           GraphRunRequest baseRequest) {
                throw new IllegalStateException("HitlPort not configured: wire adapter in application");
            }
        };
    }

    /** 装配内存工具描述索引。 */
    @Bean
    @ConditionalOnMissingBean(ToolDescriptorIndex.class)
    public ToolDescriptorIndex toolDescriptorIndex() {
        return new InMemoryToolDescriptorIndex();
    }

    /** 装配工具披露会话工厂。 */
    @Bean
    @ConditionalOnMissingBean(ToolDisclosureSessionFactory.class)
    public ToolDisclosureSessionFactory toolDisclosureSessionFactory(ToolDescriptorIndex index, ToolPort toolPort) {
        return new PassthroughToolDisclosureSessionFactory(index, toolPort);
    }
}
