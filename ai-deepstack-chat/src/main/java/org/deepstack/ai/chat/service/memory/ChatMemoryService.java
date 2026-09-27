package org.deepstack.ai.chat.service.memory;

import org.deepstack.ai.chat.model.dto.memory.request.ChatMemoryStatRequest;
import org.deepstack.ai.chat.model.dto.memory.response.ChatMemoryStatResponse;
import org.deepstack.ai.chat.model.entity.memory.ChatMemoryRecord;

import java.util.List;

/**
 * 对话记忆服务（统计 + 历史查询）
 *
 */
public interface ChatMemoryService {

    /**
     * 统计对话记忆（会话数、消息数、按日期/类型聚合）
     *
     * @param req 统计请求（可选日期范围）
     * @return 统计结果
     */
    ChatMemoryStatResponse stat(ChatMemoryStatRequest req);

    /**
     * 按会话ID物理删除所有消息
     *
     * @param conversationId 会话ID
     */
    void deleteByConversationId(String conversationId);

    /**
     * 按日期物理删除历史消息（删除 create_time &lt; 指定日期的所有记录）
     *
     * @param date 日期字符串，格式 yyyy-MM-dd
     */
    void deleteBefore(String date);

    /**
     * 按会话拉取消息历史（按 seq 升序）。
     * <p>默认排除 system 类型，仅返回 user / assistant，供前端展示。</p>
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    List<ChatMemoryRecord> listMessagesForClient(String conversationId);
}
