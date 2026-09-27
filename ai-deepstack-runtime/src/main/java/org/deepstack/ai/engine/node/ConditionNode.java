package org.deepstack.ai.engine.node;


import org.deepstack.ai.engine.WorkflowState;
import org.deepstack.ai.engine.support.ConditionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 条件节点：求值条件表达式并将结果写入变量。
 */
@Slf4j
public class ConditionNode implements AsyncNodeAction<WorkflowState> {

    private final String conditionExpression;
    private final String outputVar;

    /**
     * @param properties 节点属性：{@code conditionExpression}、{@code outputVar}
     */
    public ConditionNode(Map<String, Object> properties) {
        this.conditionExpression = (String) properties.getOrDefault("conditionExpression", "");
        this.outputVar = (String) properties.getOrDefault("outputVar", "condition_result");
    }

    /**
     * 求值条件并将 {@code true}/{@code false} 字符串写入输出变量；异常时记 warn 并视为 false。
     *
     * @param state 当前工作流状态
     * @return 含条件结果的状态增量
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowState state) {
        log.info("ConditionNode entry: expr='{}', outputVar={}", conditionExpression, outputVar);
        boolean result;
        try {
            result = ConditionEvaluator.evaluate(conditionExpression, state);
        } catch (Exception e) {
            log.warn("ConditionNode evaluation failed, defaulting to false: expr='{}', error={}", conditionExpression, e.getMessage());
            result = false;
        }
        log.info("ConditionNode result: expr='{}', result={}", conditionExpression, result);
        return CompletableFuture.completedFuture(Map.of(outputVar, result ? "true" : "false"));
    }
}
