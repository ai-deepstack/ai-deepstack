package org.deepstack.ai.engine.node;


import org.deepstack.ai.engine.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 变量赋值节点：设置/修改工作流变量。
 */
@Slf4j
public class AssignNode implements AsyncNodeAction<WorkflowState> {

    private final String variableName;
    private final String expression;

    /**
     * @param properties 节点属性：{@code variableName}、{@code expression}（支持 {@code ${var}} 引用）
     */
    public AssignNode(Map<String, Object> properties) {
        this.variableName = (String) properties.getOrDefault("variableName", "temp_var");
        this.expression = (String) properties.getOrDefault("expression", "");
    }

    /**
     * 求值表达式并写入目标变量。
     *
     * @param state 当前工作流状态
     * @return 仅含赋值结果的状态增量
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        log.info("AssignNode entry: variableName={}, expression={}", variableName, expression);
        Object value;
        if (expression != null && expression.startsWith("${") && expression.endsWith("}")) {
            String refVar = expression.substring(2, expression.length() - 1);
            value = state.value(refVar).orElse("");
        } else {
            value = expression != null ? expression : "";
        }
        log.info("AssignNode result: {}={}", variableName, value);
        return CompletableFuture.completedFuture(Map.of(variableName, value));
    }
}
