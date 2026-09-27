package org.deepstack.ai.card.web;

import org.deepstack.ai.card.model.dto.request.CardActionRequest;
import org.deepstack.ai.card.model.dto.response.CardActionResponse;
import org.deepstack.ai.card.model.dto.response.ChatCard;
import org.deepstack.ai.card.service.ChatCardService;
import org.deepstack.ai.kernel.auth.LoginUser;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话卡片管理 API。
 * <p>
 * 流式动作返回 SSE（{@code text/event-stream}）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat-cards")
@RequiredArgsConstructor
public class ChatCardController {

    private final ChatCardService chatCardService;

    /**
     * 处理卡片动作（流式响应）。
     * <p>
     * 客户端通过 WebClient 调用此接口获取 SSE 流，再透传给浏览器。
     * </p>
     *
     * @param request 卡片动作请求（cardId、action、payload 等）
     * @return SSE 事件流
     */
    @PostMapping(value = "/act/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> actStream(@RequestBody CardActionRequest request,
                                                    @LoginUser AppUserInfo loginUser) {
        applyLoginUser(request, loginUser);
        log.info("卡片动作 stream: cardId={}, action={}, conversationId={}",
                request != null ? request.getCardId() : null,
                request != null ? request.getAction() : null,
                request != null ? request.getConversationId() : null);
        return chatCardService.actStream(request);
    }

    /**
     * 处理卡片动作（非流式，仅用于简单 confirm 场景）。
     *
     * @param request 卡片动作请求（cardId、action、payload 等）
     * @return 动作处理结果
     */
    @PostMapping("/act")
    public Response<CardActionResponse> act(@RequestBody CardActionRequest request,
                                           @LoginUser AppUserInfo loginUser) {
        applyLoginUser(request, loginUser);
        log.info("卡片动作 act: cardId={}, action={}, conversationId={}",
                request != null ? request.getCardId() : null,
                request != null ? request.getAction() : null,
                request != null ? request.getConversationId() : null);
        return Response.success(chatCardService.act(request));
    }

    /** 将登录用户 ID 写入卡片动作请求。 */
    private void applyLoginUser(CardActionRequest request, AppUserInfo loginUser) {
        if (request != null && loginUser != null && loginUser.getUserId() != null) {
            request.setUserId(String.valueOf(loginUser.getUserId()));
        }
    }

    /**
     * 获取卡片详情。
     *
     * @param cardId 卡片 id
     * @return 卡片详情
     */
    @GetMapping("/{cardId}")
    public Response<ChatCard> get(@PathVariable("cardId") String cardId) {
        log.info("查询卡片详情: cardId={}", cardId);
        return Response.success(chatCardService.getByCardId(cardId));
    }

    /**
     * 查询用户已确认的卡片历史。
     *
     * @param userId   用户 id
     * @param cardType 可选卡片类型过滤
     * @param limit    返回条数上限，默认 10
     * @return 已确认卡片列表
     */
    @GetMapping("/history")
    public Response<List<ChatCard>> history(
            @RequestParam("userId") String userId,
            @RequestParam(value = "cardType", required = false) String cardType,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.info("查询卡片历史: userId={}, cardType={}, limit={}", userId, cardType, limit);
        return Response.success(chatCardService.listConfirmedCards(userId, cardType, limit));
    }
}
