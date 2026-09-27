package org.deepstack.ai.engine.node;

import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.engine.support.MessageTextExtractor;
import org.deepstack.ai.memory.MemoryPort;
import org.deepstack.ai.runtime.AgentNodeContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 长期记忆节点：经 {@link MemoryPort} 读写（PG 主存，可选图谱增强）。
 */
@Slf4j
public class MemoryNode implements AsyncNodeAction<WorkflowState> {

    private final AgentNodeContext context;
    private final String mode;
    private final boolean enableGraph;
    private final int limit;

    public MemoryNode(AgentNodeContext context, Map<String, Object> properties) {
        this.context = context;
        this.mode = String.valueOf(properties.getOrDefault("mode", "read"));
        this.enableGraph = Boolean.TRUE.equals(properties.get("enableGraph"))
                || "true".equalsIgnoreCase(String.valueOf(properties.get("enableGraph")));
        Object lim = properties.get("limit");
        this.limit = lim instanceof Number n ? n.intValue() : 10;
    }

    /** 执行节点逻辑并返回状态增量。 */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        MemoryPort port = context.getMemoryPort();
        if (port == null || !port.available()) {
            log.info("MemoryNode skip: MemoryPort unavailable");
            return CompletableFuture.completedFuture(
                    "write".equalsIgnoreCase(mode) ? Map.of() : Map.of("memory_context", ""));
        }

        String userId = context.currentUserId();
        String agentCode = context.currentAgentCode();
        String conversationId = context.currentConversationId();
        String lastText = MessageTextExtractor.lastMessageText(
                state.value(WorkflowState.MESSAGES).orElse(List.of()));

        if ("write".equalsIgnoreCase(mode)) {
            if (!StringUtils.hasText(userId) || !StringUtils.hasText(lastText)) {
                log.info("MemoryNode write skip: missing userId or message");
                return CompletableFuture.completedFuture(Map.of());
            }
            port.remember(userId, agentCode, lastText, "episode", enableGraph, List.of(), conversationId);
            return CompletableFuture.completedFuture(Map.of());
        }

        String ctx = port.recallContext(userId, agentCode, lastText, limit, enableGraph);
        log.info("MemoryNode read: userId={}, agentCode={}, graph={}, chars={}",
                userId, agentCode, enableGraph, ctx != null ? ctx.length() : 0);
        return CompletableFuture.completedFuture(Map.of("memory_context", ctx != null ? ctx : ""));
    }
}
