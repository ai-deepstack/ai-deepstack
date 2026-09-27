package org.deepstack.ai.engine.node;


import org.deepstack.ai.runtime.AgentNodeContext;
import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.engine.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用节点：调用已注册的 AgentTool。
 */
@Slf4j
public class ToolCallNode implements AsyncNodeAction<WorkflowState> {

    private final AgentNodeContext context;
    private final String toolCode;
    private final Map<String, String> paramMappings;
    private final String outputVar;

    /**
     * @param context    节点运行时上下文
     * @param properties 节点属性：{@code toolCode}、{@code paramMappings}、{@code outputVar}
     */
    @SuppressWarnings("unchecked")
    public ToolCallNode(AgentNodeContext context, Map<String, Object> properties) {
        this.context = context;
        this.toolCode = (String) properties.getOrDefault("toolCode", "");
        this.paramMappings = (Map<String, String>) properties.getOrDefault("paramMappings", Map.of());
        this.outputVar = (String) properties.getOrDefault("outputVar", "tool_result");
    }

    /**
     * 解析参数映射并执行工具，结果写入 {@code outputVar}。
     *
     * @param state 当前工作流状态
     * @return 含工具结果或错误信息的状态增量
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        log.info("ToolCallNode entry: toolCode={}, outputVar={}", toolCode, outputVar);
        if (toolCode == null || toolCode.isEmpty()) {
            log.warn("ToolCallNode: toolCode empty");
            return CompletableFuture.completedFuture(Map.of(outputVar, "错误：未选择工具"));
        }

        // 解析参数
        Map<String, Object> params = new LinkedHashMap<>();
        for (var entry : paramMappings.entrySet()) {
            String paramName = entry.getKey();
            String valueExpr = entry.getValue();
            if (valueExpr != null && valueExpr.startsWith("${") && valueExpr.endsWith("}")) {
                String varName = valueExpr.substring(2, valueExpr.length() - 1);
                params.put(paramName, state.value(varName).orElse(""));
            } else {
                params.put(paramName, valueExpr);
            }
        }

        try {
            org.deepstack.ai.engine.tool.AgentTool tool = context.getTool(toolCode);
            if (tool == null) {
                log.warn("ToolCallNode: tool not found toolCode={}", toolCode);
                return CompletableFuture.completedFuture(Map.of(outputVar, "错误：工具未找到：" + toolCode));
            }

            String result = tool.execute(params);
            log.info("ToolCallNode result: toolCode={}, outputVar={}, result={}", toolCode, outputVar, result);
            return CompletableFuture.completedFuture(Map.of(outputVar, result));
        } catch (Exception e) {
            log.error("ToolCallNode failure: toolCode={}, error={}", toolCode, e.getMessage(), e);
            return CompletableFuture.completedFuture(Map.of(outputVar, "工具调用失败：" + e.getMessage()));
        }
    }
}
