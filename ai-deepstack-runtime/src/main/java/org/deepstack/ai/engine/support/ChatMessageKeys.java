package org.deepstack.ai.engine.support;

/**
 * 对话历史条目字段（GRAPH {@code chatHistory} 与消息 Map）。
 */
public final class ChatMessageKeys {

    /** 角色（user / assistant / system 等）。 */
    public static final String ROLE = "role";
    /** 消息正文。 */
    public static final String CONTENT = "content";
    /** 正文别名（部分 Map 结构用 text）。 */
    public static final String TEXT = "text";

    /** 禁止实例化。 */
    private ChatMessageKeys() {
    }
}
