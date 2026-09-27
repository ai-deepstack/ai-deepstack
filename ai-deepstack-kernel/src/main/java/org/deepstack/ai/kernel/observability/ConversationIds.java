package org.deepstack.ai.kernel.observability;

/**
 * 会话 ID / GRAPH threadId 约定。
 */
public final class ConversationIds {

    /** 关闭短期记忆时使用的一次性会话前缀。 */
    public static final String NO_MEMORY_PREFIX = "no-memory-";

    /** GRAPH 对话 threadId：{@code conv-{conversationId}}。 */
    public static final String THREAD_CONV_PREFIX = "conv-";

    /** 禁止实例化。 */
    private ConversationIds() {
    }

    /**
     * 由会话 ID 推导 GRAPH checkpoint threadId。
     *
     * @param conversationId 会话 ID
     * @return {@code conv-} + conversationId
     */
    public static String threadIdForConversation(String conversationId) {
        return THREAD_CONV_PREFIX + conversationId;
    }

    /**
     * 生成关闭记忆时的一次性会话 ID。
     *
     * @return {@code no-memory-} + UUID
     */
    public static String newNoMemoryConversationId() {
        return NO_MEMORY_PREFIX + java.util.UUID.randomUUID();
    }

    /**
     * 解析最终会话 ID：关闭记忆时一次性 ID；否则沿用请求，空则新建 UUID。
     *
     * @param enableMemory 是否启用短期记忆
     * @param requested    请求携带的会话 ID，可为 null
     * @return 非空会话 ID
     */
    public static String resolve(boolean enableMemory, String requested) {
        if (!enableMemory) {
            return newNoMemoryConversationId();
        }
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return java.util.UUID.randomUUID().toString();
    }
}
