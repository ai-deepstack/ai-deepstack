package org.deepstack.ai.engine.node;

import org.deepstack.ai.runtime.AgentNodeContext;
import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.engine.support.MessageTextExtractor;
import org.deepstack.ai.engine.support.NodeRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RAG 知识检索节点：经 {@link org.deepstack.ai.runtime.spi.KnowledgePort#retrieveAll} 多库并行召回。
 */
@Slf4j
public class RAGRetrieveNode implements AsyncNodeAction<WorkflowState> {

    private final AgentNodeContext context;
    private final List<String> knowledgeBaseCodes;
    private final int topK;
    private final double similarityThreshold;
    private final String outputVar;
    private final int maxRetries;

    @SuppressWarnings("unchecked")
    public RAGRetrieveNode(AgentNodeContext context, Map<String, Object> properties) {
        this.context = context;
        this.knowledgeBaseCodes = (List<String>) properties.getOrDefault("knowledgeBaseCodes", List.of());
        this.topK = properties.get("topK") != null ? ((Number) properties.get("topK")).intValue() : 5;
        this.similarityThreshold = properties.get("similarityThreshold") != null
                ? ((Number) properties.get("similarityThreshold")).doubleValue() : 0.7;
        this.outputVar = (String) properties.getOrDefault("outputVar", "rag_context");
        this.maxRetries = properties.get("maxRetries") != null
                ? ((Number) properties.get("maxRetries")).intValue() : NodeRetryExecutor.DEFAULT_MAX_ATTEMPTS;
    }

    /** 执行节点逻辑并返回状态增量。 */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        String query = MessageTextExtractor.lastMessageText(state.value("messages").orElse(List.of()));
        log.info("RAGRetrieveNode entry: kbCodes={}, topK={}, threshold={}, outputVar={}",
                knowledgeBaseCodes, topK, similarityThreshold, outputVar);

        NodeRetryExecutor retryExecutor = NodeRetryExecutor.builder()
                .maxAttempts(maxRetries)
                .nodeDescription("RAGRetrieveNode")
                .build();

        List<String> results;
        try {
            // 多库并行：KnowledgePort.retrieveAll → Facade（Semaphore 限流）
            results = retryExecutor.execute(() ->
                    context.getKnowledgePort().retrieveAll(
                            knowledgeBaseCodes, query, topK, similarityThreshold));
            if (results == null) {
                results = List.of();
            }
        } catch (Exception e) {
            log.warn("RAGRetrieveNode retrieveAll 失败: kbCodes={}, err={}",
                    knowledgeBaseCodes, e.getMessage());
            results = List.of();
        }

        String ragContext = String.join("\n\n", results);
        log.info("RAGRetrieveNode result: kbCodes={}, outputVar={}, chunks={}, contextLen={}",
                knowledgeBaseCodes, outputVar, results.size(), ragContext.length());
        return CompletableFuture.completedFuture(Map.of(outputVar, ragContext));
    }
}
