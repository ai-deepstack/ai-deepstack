package org.deepstack.ai.engine.support;


import org.deepstack.ai.engine.WorkflowState;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流条件路由的表达式求值器。
 * <p>
 * 支持引用任意状态变量（intent、response、rag_context、context_vars.xxx 等），
 * 以及比较与逻辑运算符。
 * </p>
 *
 * <h3>支持的表达式语法：</h3>
 * <ul>
 *   <li><b>变量引用：</b> {@code ${varName}} 或 {@code ${context_vars.key}}</li>
 *   <li><b>比较：</b> {@code ==}、{@code !=}、{@code >}、{@code >=}、{@code <}、{@code <=}、
 *       {@code contains}、{@code !contains}、{@code matches}</li>
 *   <li><b>逻辑：</b> {@code &&}、{@code ||}</li>
 *   <li><b>空值检查：</b> {@code ${var} != null}、{@code ${var} == null}</li>
 *   <li><b>空串检查：</b> {@code ${var} != ""}、{@code ${var} == ""}</li>
 * </ul>
 *
 * <h3>示例：</h3>
 * <pre>
 *   ${intent} == FITNESS
 *   ${response} contains "运动计划"
 *   ${context_vars.user_level} >= 3
 *   ${rag_context} != null && ${intent} == CONSULTATION
 *   ${response} matches "(?i).*diet.*"
 * </pre>
 *
 */
@Slf4j
public class ConditionEvaluator {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 检测可能导致灾难性回溯的嵌套量词正则。
     * 匹配如：(a+)+、(a+)*、(a*)+、(.*)+、(\w+)+ 等。
     * 也检测带量词的分组后再跟量词：(abc){1,10}+
     * 更宽模式：内部含量词的分组，外侧再跟量词。
     */
    private static final java.util.regex.Pattern NESTED_QUANTIFIER_PATTERN =
            java.util.regex.Pattern.compile(
                // 内含量词的分组 + 外侧量词：(....[+*?]....)[+*?{]
                "\\([^)]*[+*?][^)]*\\)[+*?{]"
                // 也检测 (a|b|c)+ 类重叠分支 —— 常见 ReDoS 来源
                + "|\\([^)]*\\|[^)]*\\)[+*?]"
            );

    /**
     * 嵌套表达式最大递归深度，防止 StackOverflow。
     */
    private static final int MAX_RECURSION_DEPTH = 50;

    /**
     * 基于当前工作流状态求值条件表达式。
     *
     * @param expression 条件表达式（如 "${intent} == FITNESS && ${response} contains '运动'"）
     * @param state      当前工作流状态
     * @return 条件成立则为 true
     */
    public static boolean evaluate(String expression, WorkflowState state) {
        return evaluate(expression, state, 0);
    }

    /**
     * 带递归深度限制的求值入口。
     */
    private static boolean evaluate(String expression, WorkflowState state, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            log.warn("Condition expression exceeded max recursion depth ({}), likely deeply nested: {}...",
                    MAX_RECURSION_DEPTH, expression.substring(0, Math.min(50, expression.length())));
            return false;
        }

        if (expression == null || expression.isBlank()) {
            return true;
        }

        String trimmed = expression.trim();

        // 处理逻辑或（优先级较低）
        int orIdx = findLogicalOperator(trimmed, "||");
        if (orIdx >= 0) {
            String left = trimmed.substring(0, orIdx).trim();
            String right = trimmed.substring(orIdx + 2).trim();
            return evaluate(left, state, depth + 1) || evaluate(right, state, depth + 1);
        }

        // 处理逻辑与（优先级高于 OR）
        int andIdx = findLogicalOperator(trimmed, "&&");
        if (andIdx >= 0) {
            String left = trimmed.substring(0, andIdx).trim();
            String right = trimmed.substring(andIdx + 2).trim();
            return evaluate(left, state, depth + 1) && evaluate(right, state, depth + 1);
        }

        // 处理括号（最外层）
        if (trimmed.startsWith("(") && findMatchingParen(trimmed) == trimmed.length() - 1) {
            return evaluate(trimmed.substring(1, trimmed.length() - 1).trim(), state, depth + 1);
        }

        // 单个比较表达式
        return evaluateComparison(trimmed, state);
    }

    /**
     * 求值单个比较表达式（本层无逻辑运算符）。
     */
    private static boolean evaluateComparison(String expr, WorkflowState state) {
        // 按特异性顺序尝试各运算符（长的优先，避免部分匹配）

        // 字符串运算符
        int idx = findOperator(expr, "!contains");
        if (idx >= 0) {
            String left = resolveVar(expr.substring(0, idx).trim(), state);
            String right = unquote(expr.substring(idx + 10).trim());
            return left == null || !left.contains(right);
        }

        idx = findOperator(expr, "contains");
        if (idx >= 0) {
            String left = resolveVar(expr.substring(0, idx).trim(), state);
            String right = unquote(expr.substring(idx + 8).trim());
            return left != null && left.contains(right);
        }

        idx = findOperator(expr, "matches");
        if (idx >= 0) {
            String left = resolveVar(expr.substring(0, idx).trim(), state);
            String right = unquote(expr.substring(idx + 7).trim());
            if (left == null) return false;
            // 防 ReDoS：限制输入长度并拒绝危险模式
            if (left.length() > 10000) {
                log.warn("Regex input too long ({}), skipping matches evaluation", left.length());
                return false;
            }
            if (isPotentiallyCatastrophicRegex(right)) {
                log.warn("Potentially catastrophic regex pattern rejected: {}", right);
                return false;
            }
            try {
                return java.util.regex.Pattern.compile(right).matcher(left).find();
            } catch (Exception e) {
                log.warn("Invalid regex pattern '{}': {}", right, e.getMessage());
                return false;
            }
        }

        // 数值比较运算符
        idx = findOperator(expr, ">=");
        if (idx >= 0) {
            return compareNumeric(expr, idx, 2, state) >= 0;
        }

        idx = findOperator(expr, "<=");
        if (idx >= 0) {
            return compareNumeric(expr, idx, 2, state) <= 0;
        }

        idx = findOperator(expr, "!=");
        if (idx >= 0) {
            return evaluateNotEquals(expr, idx, state);
        }

        idx = findOperator(expr, "==");
        if (idx >= 0) {
            return evaluateEquals(expr, idx, state);
        }

        idx = findOperator(expr, ">");
        if (idx >= 0) {
            return compareNumeric(expr, idx, 1, state) > 0;
        }

        idx = findOperator(expr, "<");
        if (idx >= 0) {
            return compareNumeric(expr, idx, 1, state) < 0;
        }

        // 无运算符：按布尔检查处理（变量为真 / 非空）
        String val = resolveVar(expr, state);
        return val != null && !val.isEmpty() && !"false".equalsIgnoreCase(val) && !"0".equals(val);
    }

    // ===== 比较辅助 =====

    /** 求值 ==（含 null / 空串语义）。 */
    private static boolean evaluateEquals(String expr, int opIdx, WorkflowState state) {
        String leftRaw = expr.substring(0, opIdx).trim();
        String rightRaw = expr.substring(opIdx + 2).trim();

        String leftVal = resolveVar(leftRaw, state);
        String rightVal = resolveVarOrLiteral(rightRaw, state);

        // 空值检查
        if ("null".equalsIgnoreCase(rightRaw)) {
            return leftVal == null || leftVal.isEmpty();
        }
        // 空串检查
        if ("\"\"".equals(rightRaw) || "''".equals(rightRaw)) {
            return leftVal != null && leftVal.isEmpty();
        }

        return leftVal != null && leftVal.equals(rightVal);
    }

    /** 求值 !=（含 null / 空串语义）。 */
    private static boolean evaluateNotEquals(String expr, int opIdx, WorkflowState state) {
        String leftRaw = expr.substring(0, opIdx).trim();
        String rightRaw = expr.substring(opIdx + 2).trim();

        String leftVal = resolveVar(leftRaw, state);
        String rightVal = resolveVarOrLiteral(rightRaw, state);

        // 空值检查
        if ("null".equalsIgnoreCase(rightRaw)) {
            return leftVal != null && !leftVal.isEmpty();
        }
        // 空串检查
        if ("\"\"".equals(rightRaw) || "''".equals(rightRaw)) {
            return leftVal == null || !leftVal.isEmpty();
        }

        return leftVal == null || !leftVal.equals(rightVal);
    }

    /**
     * 对两值做数值比较；解析失败则回退为字符串比较。
     */
    private static int compareNumeric(String expr, int opIdx, int opLen, WorkflowState state) {
        String leftRaw = expr.substring(0, opIdx).trim();
        String rightRaw = expr.substring(opIdx + opLen).trim();

        String leftVal = resolveVar(leftRaw, state);
        String rightVal = resolveVarOrLiteral(rightRaw, state);

        if (leftVal == null || rightVal == null) return 0;

        try {
            double leftNum = Double.parseDouble(leftVal);
            double rightNum = Double.parseDouble(rightVal);
            return Double.compare(leftNum, rightNum);
        } catch (NumberFormatException e) {
            // 回退为字符串比较
            return leftVal.compareTo(rightVal);
        }
    }

    // ===== 变量解析 =====

    /**
     * 从 state 解析 "${intent}" 或 "${context_vars.key}" 类变量引用。
     * 若输入不是变量引用，返回 null。
     */
    static String resolveVar(String token, WorkflowState state) {
        if (token == null) return null;

        // 是否为 ${var} 引用
        Matcher m = VAR_PATTERN.matcher(token);
        if (m.matches()) {
            String varPath = m.group(1);
            return resolveStateValue(varPath, state);
        }

        // 可能是裸变量名（无 ${} 包装）
        Object val = state.value(token).orElse(null);
        if (val != null) {
            return val.toString();
        }

        return token;
    }

    /**
     * 解析可能是变量引用或字面量的 token。
     */
    private static String resolveVarOrLiteral(String token, WorkflowState state) {
        if (token == null) return null;

        // 变量引用
        Matcher m = VAR_PATTERN.matcher(token);
        if (m.matches()) {
            return resolveStateValue(m.group(1), state);
        }

        // 带引号的字符串字面量
        return unquote(token);
    }

    /**
     * 解析点路径状态值（如 "context_vars.user_level"）。
     */
    @SuppressWarnings("unchecked")
    private static String resolveStateValue(String varPath, WorkflowState state) {
        if (varPath == null || varPath.isEmpty()) return null;

        int dotIdx = varPath.indexOf('.');
        if (dotIdx > 0) {
            String topKey = varPath.substring(0, dotIdx);
            String subKey = varPath.substring(dotIdx + 1);
            Object topVal = state.value(topKey).orElse(null);
            if (topVal instanceof Map<?, ?> map) {
                Object subVal = map.get(subKey);
                return subVal != null ? subVal.toString() : null;
            }
            return null;
        }

        Object val = state.value(varPath).orElse(null);
        return val != null ? val.toString() : null;
    }

    // ===== 字符串辅助 =====

    /** 去掉首尾单/双引号。 */
    private static String unquote(String s) {
        if (s == null) return null;
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 查找不在括号或引号内的逻辑运算符（&&、||）。
     */
    private static int findLogicalOperator(String expr, String op) {
        int parenDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i <= expr.length() - op.length(); i++) {
            char c = expr.charAt(i);

            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
            else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (parenDepth == 0 && expr.startsWith(op, i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 查找不在括号或引号内的比较运算符。
     */
    private static int findOperator(String expr, String op) {
        int parenDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i <= expr.length() - op.length(); i++) {
            char c = expr.charAt(i);

            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
            else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (parenDepth == 0 && expr.startsWith(op, i)) {
                    // 避免将 != 与 !contains、>= 与 > 混淆
                    if (isOperatorBoundary(expr, i, op)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 确保运算符匹配落在正确边界（不是更长运算符的子串）。
     */
    private static boolean isOperatorBoundary(String expr, int idx, String op) {
        // 对 "contains" / "matches"，要求前面是空白
        if (op.matches("[a-zA-Z]+")) {
            if (idx > 0 && !Character.isWhitespace(expr.charAt(idx - 1))) return false;
        }

        // 对 ">"，确保不是 ">="
        if (">".equals(op) && idx + 1 < expr.length() && expr.charAt(idx + 1) == '=') return false;
        // 对 "<"，确保不是 "<="
        if ("<".equals(op) && idx + 1 < expr.length() && expr.charAt(idx + 1) == '=') return false;

        return true;
    }

    /**
     * 查找位置 0 处开括号对应的闭括号位置。
     */
    private static int findMatchingParen(String expr) {
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '(') depth++;
            else if (expr.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * 判断正则是否可能造成灾难性回溯（ReDoS）。
     * 检测：
     * - 嵌套量词：(a+)+、(a+)*、(.*)+、(\w+)+
     * - 重叠分支加量词：(a|a)+、(a|ab)+
     * - 量词分组后再跟量词：(abc){1,10}+
     * 另依赖输入长度上限（10000 字符）作为二级守卫。
     */
    private static boolean isPotentiallyCatastrophicRegex(String pattern) {
        if (pattern == null) return false;
        // 检测嵌套量词与重叠分支
        if (NESTED_QUANTIFIER_PATTERN.matcher(pattern).find()) return true;
        // 检测 .*.* 或 .+.+（同一字符上连续贪婪量词）
        if (pattern.matches(".*\\. [+*?][+*?].*")) return true;
        return false;
    }
}
