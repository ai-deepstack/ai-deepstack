package org.deepstack.ai.chat.web;

import org.deepstack.ai.chat.model.dto.request.ChatRequest;
import org.deepstack.ai.chat.model.dto.response.ChatResponse;
import org.deepstack.ai.kernel.auth.LoginUser;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.deepstack.ai.chat.service.ChatService;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 对话管理 API（客户端）
 * <p>
 * 单体统一 API。
 * 接收 ai-api 的 DTO，内部转换为 ai-server 的 DTO 后委托给 ChatService。
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 带记忆的对话（非流式）。
     *
     * @param request   对话请求（agentCode、message、conversationId 等）
     * @param loginUser 当前登录用户（注入 userId）
     * @return 完整回复与可选卡片/用量
     */
    @PostMapping("/send")
    public Response<ChatResponse> send(@RequestBody ChatRequest request,
                                       @LoginUser AppUserInfo loginUser) {
        log.info("对话 send: agentCode={}, conversationId={}, userId={}",
                request != null ? request.getAgentCode() : null,
                request != null ? request.getConversationId() : null,
                loginUser != null ? loginUser.getUserId() : null);
        applyLoginUser(request, loginUser);
        org.deepstack.ai.chat.model.dto.internal.request.ChatRequest innerReq = toInnerRequest(request);
        org.deepstack.ai.chat.model.dto.internal.response.ChatResponse innerResp = chatService.chat(innerReq);
        return Response.success(toApiResponse(innerResp));
    }

    /**
     * 带记忆的对话（SSE 流式）。
     * <p>
     * 客户端通过 WebClient 调用此接口获取 SSE 流，再透传给浏览器。
     * 内容 chunk 为默认事件；流结束前会发出一条 event=usage 的 metadata 事件。
     * </p>
     *
     * @param request   对话请求（agentCode、message、conversationId 等）
     * @param loginUser 当前登录用户（注入 userId）
     * @return SSE 事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request,
                                                @LoginUser AppUserInfo loginUser) {
        log.info("对话 stream: agentCode={}, conversationId={}, userId={}",
                request != null ? request.getAgentCode() : null,
                request != null ? request.getConversationId() : null,
                loginUser != null ? loginUser.getUserId() : null);
        applyLoginUser(request, loginUser);
        org.deepstack.ai.chat.model.dto.internal.request.ChatRequest innerReq = toInnerRequest(request);
        return chatService.streamChat(innerReq);
    }

    /** 将登录用户 ID 写入请求（若已登录）。 */
    private void applyLoginUser(ChatRequest request, AppUserInfo loginUser) {
        if (loginUser != null && loginUser.getUserId() != null) {
            request.setUserId(String.valueOf(loginUser.getUserId()));
        }
    }

    // ===== DTO 转换 =====

    /** API DTO → 内部 ChatRequest。 */
    private org.deepstack.ai.chat.model.dto.internal.request.ChatRequest toInnerRequest(ChatRequest req) {
        org.deepstack.ai.chat.model.dto.internal.request.ChatRequest inner = new org.deepstack.ai.chat.model.dto.internal.request.ChatRequest();
        inner.setAgentCode(req.getAgentCode());
        inner.setUserId(req.getUserId());
        inner.setConversationId(req.getConversationId());
        inner.setMessage(req.getMessage());
        return inner;
    }

    /** 内部 ChatResponse → API DTO。 */
    private ChatResponse toApiResponse(org.deepstack.ai.chat.model.dto.internal.response.ChatResponse resp) {
        ChatResponse api = new ChatResponse(resp.getConversationId(), resp.getContent());
        api.setCards(resp.getCards());
        api.setPromptTokens(resp.getPromptTokens());
        api.setCompletionTokens(resp.getCompletionTokens());
        api.setTotalTokens(resp.getTotalTokens());
        api.setFirstTokenLatencyMs(resp.getFirstTokenLatencyMs());
        api.setLatencyMs(resp.getLatencyMs());
        return api;
    }
}
