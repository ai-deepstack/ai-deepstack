package org.deepstack.ai.chat.support;

import org.springframework.http.codec.ServerSentEvent;

/**
 * 对话 SSE 帧构造。事件名见 {@link org.deepstack.ai.kernel.observability.ChatSseEvents}。
 */
public final class ChatSse {

    /** agent_error / 错误 payload 中的错误字段键。 */
    public static final String ERROR = "error";

    /** 禁止实例化。 */
    private ChatSse() {
    }

    /**
     * 带事件名的 SSE 帧。
     *
     * @param event 事件名（如 message / usage）
     * @param data  数据；null 视为空串
     * @return SSE 帧
     */
    public static ServerSentEvent<String> of(String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data != null ? data : "")
                .build();
    }

    /**
     * 仅 data、无事件名的 SSE 帧（CHAT 流式 token chunk）。
     *
     * @param data 文本 chunk；null 视为空串
     * @return SSE 帧
     */
    public static ServerSentEvent<String> data(String data) {
        return ServerSentEvent.<String>builder()
                .data(data != null ? data : "")
                .build();
    }
}
