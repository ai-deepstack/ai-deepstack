package org.deepstack.ai.runtime.config;

import org.deepstack.ai.engine.tool.AgentTool;
import org.deepstack.ai.engine.api.GraphRunRequest;
import org.deepstack.ai.engine.api.GraphRunResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * 缺省空端口：未在应用层装配 Adapter 时仍可启动 runtime。
 * <p>
 * 占位策略统一为「调用即失败」：宿主显式配置了某项能力（绑了工具 / 绑了知识库 / 选了模型）
 * 却没有引入对应 Adapter 时，静默返回空会被误读成「检索没命中」「工具没产生输出」，
 * 而实际是能力根本没接上。这里统一抛 {@link IllegalStateException} 并带上缺失的模块名。
 * </p>
 * <p>
 * 例外：{@code MemoryPort} / {@code HitlPort} 属于「可选能力」，调用方通过 {@code available()}
 * 判断后再用，因此这两个的占位实现只标记不可用，不抛异常。
 * </p>
 * <p>
 * <b>必须是自动配置，不能是普通 {@code @Configuration}</b>：这里的 {@code @ConditionalOnMissingBean}
 * 依赖「已处理的 Bean 定义」来判断，而组件扫描的顺序由 classpath 决定——runtime 在 fat jar 里排在
 * 各能力模块之前，写成普通配置会让占位实现先注册、把真正的 Adapter（infra / tool / knowledge /
 * memory-pg / card / agent）全部顶掉。注册为自动配置后，Spring Boot 保证它在所有组件之后处理
 * （并由 {@code AutoConfigurationExcludeFilter} 把它排除出组件扫描），判定才是确定的。
 * </p>
 */
@Slf4j
@AutoConfiguration
public class RuntimeSpiAutoConfiguration {

    /** 未装配端口时的统一异常，带上应该引入的模块名，便于定位。 */
    private static IllegalStateException notConfigured(String portName, String adapterModule) {
        return new IllegalStateException(portName + " not configured: wire " + adapterModule
                + " (or another adapter implementing " + portName + ") in the host application");
    }

    /** 未装配模型端口时的占位实现。 */
    @Bean
    @ConditionalOnMissingBean(ModelPort.class)
    public ModelPort missingModelPort() {
        log.warn("ModelPort 未装配：模型调用将失败。宿主请引入 ai-deepstack-infra（或其等价实现）。");
        return new ModelPort() {
            /** 返回 ChatModel。 */
            @Override
            public ChatModel getChatModel(String modelCode) {
                throw notConfigured("ModelPort", "ai-deepstack-infra");
            }

            /** 返回 ApiModelName。 */
            @Override
            public String getApiModelName(String modelCode) {
                throw notConfigured("ModelPort", "ai-deepstack-infra");
            }
        };
    }

    /**
     * 未装配工具端口时的占位实现。
     * <p>
     * 不返回空列表：Agent 已经绑定了工具却拿不到回调时，空结果会让它「看起来正常但一个工具都没调」，
     * 属于最难排查的一类故障，因此直接抛异常。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(ToolPort.class)
    public ToolPort missingToolPort() {
        log.warn("ToolPort 未装配：Agent 绑定的工具将无法解析。宿主请引入 ai-deepstack-tool（或其等价实现）。");
        return new ToolPort() {
            /** 按工具编码解析回调；未装配时直接失败。 */
            @Override
            public List<ToolCallback> resolveToolCallbacksByCodes(List<String> toolCodes) {
                throw notConfigured("ToolPort", "ai-deepstack-tool");
            }

            /** 取单个工具定义；未装配时直接失败。 */
            @Override
            public AgentTool getAgentTool(String toolCode) {
                throw notConfigured("ToolPort", "ai-deepstack-tool");
            }

            /** 列出已注册工具；未装配时直接失败。 */
            @Override
            public List<AgentTool> listAgentTools() {
                throw notConfigured("ToolPort", "ai-deepstack-tool");
            }
        };
    }

    /**
     * 未装配知识端口时的占位实现。
     * <p>
     * 同理：绑了知识库却检索不到任何内容，会被误读成「没命中」而不是「知识库模块没接上」。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(KnowledgePort.class)
    public KnowledgePort missingKnowledgePort() {
        log.warn("KnowledgePort 未装配：知识库检索将失败。宿主请引入 ai-deepstack-knowledge（或其等价实现）。");
        return new KnowledgePort() {
            /** 检索单个知识库；未装配时直接失败。 */
            @Override
            public List<String> retrieve(String baseCode, String query, int topK, double similarityThreshold) {
                throw notConfigured("KnowledgePort", "ai-deepstack-knowledge");
            }

            /** 检索多个知识库；未装配时直接失败。 */
            @Override
            public List<String> retrieveAll(List<String> baseCodes, String query,
                                            int topK, double similarityThreshold) {
                throw notConfigured("KnowledgePort", "ai-deepstack-knowledge");
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

    /**
     * 未装配记忆端口时的占位实现。
     * <p>
     * 长期记忆属可选能力：调用方先看 {@code available()} 再决定要不要用，
     * 因此这里只标记不可用，不抛异常（见 {@code MemoryNode}）。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(MemoryPort.class)
    public MemoryPort missingMemoryPort() {
        log.info("MemoryPort 未装配：长期记忆按关闭处理（可用时请引入 ai-deepstack-memory-pg）。");
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

    /**
     * 未装配 HITL 端口时的占位实现。
     * <p>
     * 与记忆同理属可选能力：{@code available()} 为假时调用方不会走恢复流程。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(HitlPort.class)
    public HitlPort missingHitlPort() {
        log.info("HitlPort 未装配：人工确认恢复按不可用处理（可用时请引入 ai-deepstack-agent）。");
        return new HitlPort() {
            /** 当前能力是否可用。 */
            @Override
            public boolean available() {
                return false;
            }

            /** 恢复 HITL 挂起的图运行；未装配时直接失败。 */
            @Override
            public GraphRunResponse resume(String agentCode, String threadId, Map<String, Object> stateUpdates,
                                           GraphRunRequest baseRequest) {
                throw notConfigured("HitlPort", "ai-deepstack-agent");
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
