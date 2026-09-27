package org.deepstack.ai.chat.service;

import org.deepstack.ai.chat.model.entity.ChatConversation;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 对话会话服务
 * <p>
 * 管理会话元信息，主要用于 reject 重新生成等跨请求场景反查 agentCode / modelId。
 * </p>
 *
 */
public interface ChatConversationService extends IService<ChatConversation> {

    /**
     * 按 conversationId 查询会话（未删除）
     *
     * @param conversationId 业务会话 ID
     * @return 会话记录，不存在返回 null
     */
    ChatConversation getByConversationId(String conversationId);

    /**
     * 获取或创建会话：首次对话插入新记录，后续消息更新 last_message_at 和 message_count。
     *
     * @param conversationId 业务会话 ID
     * @param agentCode      智能体编码
     * @param userId         用户 ID（可空）
     * @return 已存在或新建的会话记录
     */
    ChatConversation getOrCreate(String conversationId, String agentCode, String userId);

    /**
     * 分页查询用户会话列表（按最后消息时间倒序）。
     *
     * @param userId   用户 ID
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<ChatConversation> pageByUserId(String userId, long pageNum, long pageSize);

    /**
     * 校验会话归属当前用户；不存在或无权访问时抛业务异常。
     *
     * @param conversationId 业务会话 ID
     * @param userId         当前用户 ID
     * @return 会话记录
     */
    ChatConversation requireOwned(String conversationId, String userId);
}
