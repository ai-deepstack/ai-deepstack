package org.deepstack.ai.engine.support;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * 工作流节点用的消息文本抽取工具。
 * <p>
 * Spring AI {@link Message#toString()} 可能包含类型前缀，不适合作为 RAG / Memory 查询文本。
 * 优先使用 {@link Message#getText()}；对字符串或 Map 结构做宽松回退。
 * </p>
 *
 */
public final class MessageTextExtractor {

    private MessageTextExtractor() {
    }

    /**
     * 从 messages 列表末尾提取用户可读查询文本；列表为空时返回空串。
     *
     * @param messagesObj 通常是 {@code List} of Message / String / Map
     * @return 非 null 文本
     */
    public static String lastMessageText(Object messagesObj) {
        if (!(messagesObj instanceof List<?> msgs) || msgs.isEmpty()) {
            return "";
        }
        return textOf(msgs.get(msgs.size() - 1));
    }

    /**
     * 将单个消息对象转为纯文本。
     */
    public static String textOf(Object msg) {
        if (msg == null) {
            return "";
        }
        if (msg instanceof Message m) {
            String text = m.getText();
            return text != null ? text : "";
        }
        if (msg instanceof String s) {
            return s;
        }
        if (msg instanceof Map<?, ?> map) {
            Object content = map.get(ChatMessageKeys.CONTENT);
            if (content == null) {
                content = map.get(ChatMessageKeys.TEXT);
            }
            return content != null ? content.toString() : "";
        }
        return msg.toString();
    }
}
