package org.deepstack.ai.card.service;

import org.deepstack.ai.card.model.dto.request.CardActionRequest;
import org.deepstack.ai.card.model.dto.response.CardActionResponse;
import org.deepstack.ai.card.model.dto.response.ChatCard;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 对话卡片服务
 * <p>
 * 负责卡片的生命周期管理：
 * <ul>
 *   <li>创建卡片（由 AI 节点产出时调用）</li>
 *   <li>处理用户动作（confirm / edit / reject）</li>
 *   <li>查询卡片历史</li>
 * </ul>
 * </p>
 *
 */
public interface ChatCardService {

    /**
     * 保存卡片（AI 产出卡片时调用）
     *
     * @param card 卡片数据
     * @return 保存后的卡片（含数据库 ID）
     */
    ChatCard saveCard(ChatCard card);

    /**
     * 根据 cardId 获取卡片。
     *
     * @param cardId 业务卡片 ID
     * @return 卡片；不存在返回 null
     */
    ChatCard getByCardId(String cardId);

    /**
     * 处理用户对卡片的动作（流式响应）
     * <p>
     * confirm → 调 CardProcessor.persist() 落地业务 → 返回成功消息流
     * edit    → 用 modifiedPayload 重新产出卡片 → 返回新卡片流
     * reject  → 重新触发 planDraft → 返回新卡片流
     * </p>
     *
     * @param request 动作请求
     * @return SSE 流（卡片或文本消息）
     */
    Flux<ServerSentEvent<Object>> actStream(CardActionRequest request);

    /**
     * 处理用户对卡片的动作（非流式）
     * <p>
     * 仅用于 confirm 场景的简单响应；edit/reject 建议用 {@link #actStream}。
     * </p>
     *
     * @param request 动作请求
     * @return 动作结果
     */
    CardActionResponse act(CardActionRequest request);

    /**
     * 查询用户已确认的卡片历史
     *
     * @param userId   用户 ID
     * @param cardType 卡片类型（可选，为空查全部）
     * @param limit    最多返回条数
     * @return 已确认卡片列表
     */
    List<ChatCard> listConfirmedCards(String userId, String cardType, int limit);

    /**
     * 构建卡片 payload（供 Spring AI tool 调用）。
     *
     * @param cardType 卡片类型（须已注册 {@link org.deepstack.ai.card.service.spi.CardProcessor}）
     * @param params   LLM 提供的参数
     * @return 构建后的 payload
     */
    Map<String, Object> buildPayload(String cardType, Map<String, Object> params);
}
