package org.deepstack.ai.engine.node;


import org.deepstack.ai.runtime.AgentNodeContext;
import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.engine.support.MessageTextExtractor;
import org.deepstack.ai.engine.support.NodeRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 意图分类节点：用 LLM 将用户消息归入配置的类别之一。
 */
@Slf4j
public class IntentClassifierNode implements AsyncNodeAction<WorkflowState> {

    /** 未配置类别、无模型或分类失败时的默认意图。 */
    static final String FALLBACK_CATEGORY = "GENERAL";

    private final AgentNodeContext context;
    private final List<String> categories;
    private final String modelCode;
    private final String outputVar;
    private final int maxRetries;

    /**
     * @param context    节点运行时上下文
     * @param properties 节点属性：{@code categories}（逗号分隔字符串或列表）、{@code modelCode}、
     *                   {@code outputVar}、{@code maxRetries}
     */
    public IntentClassifierNode(AgentNodeContext context, Map<String, Object> properties) {
        this.context = context;
        this.categories = parseCategories(properties.get("categories"));
        Object mc = properties.get("modelCode");
        this.modelCode = mc != null ? mc.toString() : null;
        Object ov = properties.get("outputVar");
        String out = ov != null ? ov.toString().trim() : "";
        this.outputVar = out.isEmpty() ? WorkflowState.INTENT : out;
        this.maxRetries = properties.get("maxRetries") != null
                ? ((Number) properties.get("maxRetries")).intValue() : NodeRetryExecutor.DEFAULT_MAX_ATTEMPTS;
    }

    /** {@code categories}：字符串列表，或逗号分隔字符串。 */
    @SuppressWarnings("unchecked")
    private static List<String> parseCategories(Object raw) {
        if (raw == null) {
            return List.of(FALLBACK_CATEGORY);
        }
        if (raw instanceof Collection<?> collection) {
            List<String> list = new ArrayList<>();
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                String s = item.toString().trim();
                if (!s.isEmpty()) {
                    list.add(s);
                }
            }
            return list.isEmpty() ? List.of(FALLBACK_CATEGORY) : List.copyOf(list);
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return List.of(FALLBACK_CATEGORY);
        }
        List<String> list = Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return list.isEmpty() ? List.of(FALLBACK_CATEGORY) : list;
    }

    /**
     * 分类最近一条用户消息，结果写入 {@code outputVar}（默认 {@code intent}）；
     * 失败或无模型时回退 {@link #FALLBACK_CATEGORY}。
     *
     * @param state 当前工作流状态
     * @return 含输出变量的状态增量
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        log.info("IntentClassifierNode entry: modelCode={}, categories={}, outputVar={}, maxRetries={}",
                modelCode, categories, outputVar, maxRetries);
        if (modelCode == null || modelCode.isBlank()) {
            log.warn("IntentClassifierNode: modelCode is null, falling back to {}", FALLBACK_CATEGORY);
            return CompletableFuture.completedFuture(Map.of(outputVar, FALLBACK_CATEGORY));
        }

        String userMessage = lastMessage(state);
        try {
            String systemPrompt = "你是一个意图分类器。请将用户消息分类到以下类别之一："
                    + String.join("、", categories) + "。只返回类别名称，不要有任何其他内容。";

            NodeRetryExecutor retryExecutor = NodeRetryExecutor.builder()
                    .maxAttempts(maxRetries)
                    .nodeDescription("IntentClassifierNode")
                    .build();

            ChatResponse resp = retryExecutor.execute(() -> {
                OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                        .model(context.getApiModelName(modelCode))
                        .temperature(0.0)
                        .build();
                Prompt prompt = new Prompt(
                        List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)),
                        chatOptions
                );
                return context.getChatModel(modelCode).call(prompt);
            });

            String intent = "";
            if (resp.getResult() != null && resp.getResult().getOutput() != null
                    && resp.getResult().getOutput().getText() != null) {
                intent = resp.getResult().getOutput().getText().trim().toUpperCase();
            }
            if (intent.isEmpty()) {
                intent = FALLBACK_CATEGORY;
            }
            log.info("IntentClassifierNode result: msg='{}', {}='{}'", userMessage, outputVar, intent);
            return CompletableFuture.completedFuture(Map.of(outputVar, intent));
        } catch (Exception e) {
            log.warn("IntentClassifierNode failure, fallback {} after {} attempts: {}",
                    FALLBACK_CATEGORY, maxRetries, e.getMessage());
            return CompletableFuture.completedFuture(Map.of(outputVar, FALLBACK_CATEGORY));
        }
    }

    /** 从工作流状态中取最后一条用户/对话消息文本。 */
    private static String lastMessage(WorkflowState state) {
        return MessageTextExtractor.lastMessageText(state.value(WorkflowState.MESSAGES).orElse(List.of()));
    }
}
