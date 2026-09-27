package org.deepstack.ai.kernel.p6spy;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 当前线程正在执行的 MyBatis MappedStatement id（Mapper 全名.方法名），供 P6Spy 日志读取。
 * <p>使用栈以支持嵌套查询，避免内层 SQL 结束后清掉外层上下文。</p>
 */
public final class MybatisSqlContext {

    private static final ThreadLocal<Deque<String>> MAPPER_ID_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private MybatisSqlContext() {
    }

    /** 进入一条 Mapper 语句。 */
    public static void push(String mapperId) {
        if (mapperId == null || mapperId.isBlank()) {
            return;
        }
        MAPPER_ID_STACK.get().push(mapperId);
    }

    /** 离开当前 Mapper 语句。 */
    public static void pop() {
        Deque<String> stack = MAPPER_ID_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            MAPPER_ID_STACK.remove();
        }
    }

    /** 当前最内层 Mapper id；无则返回 null。 */
    public static String peek() {
        Deque<String> stack = MAPPER_ID_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }
}
