package org.deepstack.ai.chat.service;

import org.deepstack.ai.chat.model.dto.internal.request.ChatRequest;
import org.deepstack.ai.chat.model.dto.internal.response.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * AI 对话服务
 *
 */
public interface ChatService {

    /**
     * 带记忆的对话（非流式）
     *
     * @param request 对话请求
     * @return 对话响应（含 token 统计 + 总耗时）
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 带记忆的流式对话。
     * <p>
     * 输出包含以下事件类型：
     * <ul>
     *   <li><b>message</b>（默认 event，无 event 字段）：data 为内容 chunk 字符串</li>
     *   <li><b>card</b>：data 为 JSON，AI 产出的结构化卡片，前端按 cardType 渲染</li>
     *   <li><b>usage</b>（最后一条）：data 为 JSON，含 token 统计与耗时</li>
     * </ul>
     * </p>
     *
     * @param request 对话请求
     * @return SSE 流
     */
    Flux<ServerSentEvent<String>> streamChat(ChatRequest request);
}
