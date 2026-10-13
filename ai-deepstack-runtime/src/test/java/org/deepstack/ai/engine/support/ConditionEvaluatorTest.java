package org.deepstack.ai.engine.support;

import org.deepstack.ai.engine.api.WorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConditionEvaluator} 的求值语义测试。
 * <p>
 * 该求值器决定图的走向，算错等于静默走错分支，因此把运算符判定、空值语义
 * 与安全防护（递归深度、ReDoS）都固定下来。
 * </p>
 */
class ConditionEvaluatorTest {

    private static WorkflowState state(Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>();
        data.put(WorkflowState.RESPONSE, "");
        data.put(WorkflowState.INTENT, "");
        data.put(WorkflowState.CONTEXT_VARS, new HashMap<String, Object>());
        data.putAll(extra);
        return new WorkflowState(data);
    }

    private static WorkflowState stateWithIntent(String intent) {
        return state(Map.of(WorkflowState.INTENT, intent));
    }

    @Nested
    @DisplayName("空表达式")
    class Blank {

        @Test
        @DisplayName("null 与空白视为成立")
        void blankIsTrue() {
            assertThat(ConditionEvaluator.evaluate(null, state(Map.of()))).isTrue();
            assertThat(ConditionEvaluator.evaluate("", state(Map.of()))).isTrue();
            assertThat(ConditionEvaluator.evaluate("   ", state(Map.of()))).isTrue();
        }
    }

    @Nested
    @DisplayName("相等与空值语义")
    class Equals {

        @Test
        @DisplayName("字符串相等")
        void stringEquals() {
            assertThat(ConditionEvaluator.evaluate("${intent} == FITNESS", stateWithIntent("FITNESS"))).isTrue();
            assertThat(ConditionEvaluator.evaluate("${intent} == FITNESS", stateWithIntent("CONSULT"))).isFalse();
        }

        @Test
        @DisplayName("未定义的变量等于 null")
        void missingVariableIsNull() {
            assertThat(ConditionEvaluator.evaluate("${missing} == null", state(Map.of()))).isTrue();
            assertThat(ConditionEvaluator.evaluate("${intent} == null", stateWithIntent("FITNESS"))).isFalse();
        }

        @Test
        @DisplayName("空串判定")
        void emptyStringCheck() {
            assertThat(ConditionEvaluator.evaluate("${response} == \"\"", state(Map.of()))).isTrue();
            assertThat(ConditionEvaluator.evaluate("${response} == \"\"",
                    state(Map.of(WorkflowState.RESPONSE, "x")))).isFalse();
        }

        @Test
        @DisplayName("不等于的 null 与空串语义")
        void notEqualsNullAndEmpty() {
            assertThat(ConditionEvaluator.evaluate("${intent} != null", stateWithIntent("FITNESS"))).isTrue();
            assertThat(ConditionEvaluator.evaluate("${missing} != null", state(Map.of()))).isFalse();
            assertThat(ConditionEvaluator.evaluate("${response} != \"\"",
                    state(Map.of(WorkflowState.RESPONSE, "x")))).isTrue();
        }
    }

    @Nested
    @DisplayName("字符串运算符")
    class StringOps {

        @Test
        @DisplayName("contains 与 !contains")
        void containsAndNotContains() {
            WorkflowState s = state(Map.of(WorkflowState.RESPONSE, "这是一份运动计划"));
            assertThat(ConditionEvaluator.evaluate("${response} contains \"运动\"", s)).isTrue();
            assertThat(ConditionEvaluator.evaluate("${response} contains \"饮食\"", s)).isFalse();
            assertThat(ConditionEvaluator.evaluate("${response} !contains \"饮食\"", s)).isTrue();
        }

        @Test
        @DisplayName("contains 对未定义变量为假")
        void containsOnMissingIsFalse() {
            assertThat(ConditionEvaluator.evaluate("${missing} contains \"x\"", state(Map.of()))).isFalse();
        }

        @Test
        @DisplayName("matches 使用 find 语义")
        void matchesUsesFind() {
            WorkflowState s = state(Map.of(WorkflowState.RESPONSE, "give me a Diet plan"));
            assertThat(ConditionEvaluator.evaluate("${response} matches \"(?i)diet\"", s)).isTrue();
            assertThat(ConditionEvaluator.evaluate("${response} matches \"^diet$\"", s)).isFalse();
        }

        @Test
        @DisplayName("非法正则为假而不抛异常")
        void invalidRegexIsFalse() {
            WorkflowState s = state(Map.of(WorkflowState.RESPONSE, "abc"));
            assertThat(ConditionEvaluator.evaluate("${response} matches \"[unclosed\"", s)).isFalse();
        }

        @Test
        @DisplayName("裸变量名也能解析")
        void bareVariableName() {
            WorkflowState s = state(Map.of(WorkflowState.RESPONSE, "hello"));
            assertThat(ConditionEvaluator.evaluate("response contains \"ell\"", s)).isTrue();
        }
    }

    @Nested
    @DisplayName("数值比较")
    class Numeric {

        @Test
        @DisplayName("点路径取值并比较")
        void dotPathNumericCompare() {
            WorkflowState s = state(Map.of(WorkflowState.CONTEXT_VARS, Map.of("user_level", 3)));
            assertThat(ConditionEvaluator.evaluate("${context_vars.user_level} >= 3", s)).isTrue();
            assertThat(ConditionEvaluator.evaluate("${context_vars.user_level} > 3", s)).isFalse();
            assertThat(ConditionEvaluator.evaluate("${context_vars.user_level} < 5", s)).isTrue();
        }

        @Test
        @DisplayName("大于号不会误配大于等于")
        void gtDoesNotMatchGte() {
            WorkflowState s = state(Map.of(WorkflowState.CONTEXT_VARS, Map.of("n", 3)));
            // n = 3：>= 3 成立、> 3 不成立；若 ">" 抢在 ">=" 之前匹配就会出错
            assertThat(ConditionEvaluator.evaluate("${context_vars.n} >= 3", s)).isTrue();
            assertThat(ConditionEvaluator.evaluate("${context_vars.n} > 3", s)).isFalse();
        }

        @Test
        @DisplayName("非数值回退字符串比较")
        void nonNumericFallsBackToString() {
            assertThat(ConditionEvaluator.evaluate("${intent} > AAA", stateWithIntent("BBB"))).isTrue();
            assertThat(ConditionEvaluator.evaluate("${intent} < AAA", stateWithIntent("BBB"))).isFalse();
        }
    }

    @Nested
    @DisplayName("逻辑组合")
    class Logical {

        @Test
        @DisplayName("与 的优先级高于 或")
        void andBindsTighterThanOr() {
            WorkflowState s = stateWithIntent("FITNESS");
            // A || B && C 应读作 A || (B && C)：A 为真即为真；
            // 若误按从左到右算成 (A || B) && C，会因 C 为假而变假
            assertThat(ConditionEvaluator.evaluate(
                    "${intent} == FITNESS || ${intent} == X && 1 == 2", s)).isTrue();
            assertThat(ConditionEvaluator.evaluate(
                    "(${intent} == FITNESS || ${intent} == X) && 1 == 2", s)).isFalse();
        }

        @Test
        @DisplayName("括号内的运算符不被当作顶层切分")
        void parensAreNotTopLevelSplit() {
            WorkflowState s = stateWithIntent("FITNESS");
            assertThat(ConditionEvaluator.evaluate(
                    "${intent} == NOPE && (${intent} == FITNESS || 1 == 2)", s)).isFalse();
            assertThat(ConditionEvaluator.evaluate(
                    "${intent} == FITNESS && (${intent} == FITNESS || 1 == 2)", s)).isTrue();
        }
    }

    @Nested
    @DisplayName("无运算符的真值判定")
    class Truthiness {

        @Test
        @DisplayName("非空且非假值即为真")
        void nonEmptyIsTruthy() {
            assertThat(ConditionEvaluator.evaluate("${intent}", stateWithIntent("FITNESS"))).isTrue();
            assertThat(ConditionEvaluator.evaluate("${missing}", state(Map.of()))).isFalse();
            assertThat(ConditionEvaluator.evaluate("${intent}", stateWithIntent(""))).isFalse();
            assertThat(ConditionEvaluator.evaluate("${intent}", stateWithIntent("false"))).isFalse();
            assertThat(ConditionEvaluator.evaluate("${intent}", stateWithIntent("0"))).isFalse();
        }
    }

    @Nested
    @DisplayName("安全防护")
    class Guards {

        @Test
        @DisplayName("递归过深返回假而不是栈溢出")
        void tooDeepReturnsFalse() {
            StringBuilder sb = new StringBuilder();
            sb.append("(".repeat(60));
            sb.append("${intent} == FITNESS");
            sb.append(")".repeat(60));
            assertThat(ConditionEvaluator.evaluate(sb.toString(), stateWithIntent("FITNESS"))).isFalse();
        }

        @Test
        @DisplayName("嵌套量词正则被拒绝且不挂起")
        void catastrophicRegexRejected() {
            WorkflowState s = state(Map.of(WorkflowState.RESPONSE, "aaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
            // (a+)+b 属灾难性回溯，求值器应直接判假而不是进入匹配
            assertThat(ConditionEvaluator.evaluate("${response} matches \"(a+)+b\"", s)).isFalse();
        }

        @Test
        @DisplayName("超长输入不进入正则")
        void tooLongInputSkipped() {
            WorkflowState s = state(Map.of(WorkflowState.RESPONSE, "a".repeat(10001)));
            assertThat(ConditionEvaluator.evaluate("${response} matches \"a\"", s)).isFalse();
        }
    }

    @Test
    @DisplayName("列表值取不到点路径时按假处理")
    void listDotPathIsFalse() {
        WorkflowState s = state(Map.of(WorkflowState.MESSAGES, List.of("a", "b")));
        assertThat(ConditionEvaluator.evaluate("${messages.0} == a", s)).isFalse();
    }
}
