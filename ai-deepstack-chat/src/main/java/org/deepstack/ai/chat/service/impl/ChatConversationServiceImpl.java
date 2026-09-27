package org.deepstack.ai.chat.service.impl;

import org.deepstack.ai.chat.model.entity.ChatConversation;
import org.deepstack.ai.chat.mapper.ChatConversationMapper;
import org.deepstack.ai.chat.service.ChatConversationService;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 对话会话服务实现
 *
 */
@Slf4j
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation> implements ChatConversationService {

    /**
     * 按业务会话 ID 查询未删除会话。
     *
     * @param conversationId 业务会话 ID
     * @return 会话；为空或未找到返回 null
     */
    @Override
    public ChatConversation getByConversationId(String conversationId) {
        log.info("getByConversationId: conversationId={}", conversationId);
        if (conversationId == null || conversationId.isEmpty()) {
            log.warn("getByConversationId 跳过: conversationId 为空");
            return null;
        }
        ChatConversation conversation = baseMapper.selectOne(
                new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getConversationId, conversationId)
                        .eq(ChatConversation::getIsDel, 0));
        if (conversation == null) {
            log.warn("会话未找到: conversationId={}", conversationId);
        }
        return conversation;
    }

    /**
     * 获取或创建会话；已存在则更新 last_message_at 与 message_count。
     *
     * @param conversationId 业务会话 ID
     * @param agentCode      智能体编码
     * @param userId         用户 ID
     * @return 会话实体
     */
    @Override
    public ChatConversation getOrCreate(String conversationId, String agentCode, String userId) {
        log.info("getOrCreate: conversationId={}, agentCode={}, userId={}",
                conversationId, agentCode, userId);
        // 内部直查，避免「首次创建」路径误打 not-found warn
        ChatConversation existing = (conversationId == null || conversationId.isEmpty())
                ? null
                : baseMapper.selectOne(
                new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getConversationId, conversationId)
                        .eq(ChatConversation::getIsDel, 0));
        if (existing != null) {
            if (StringUtils.hasText(existing.getUserId())
                    && StringUtils.hasText(userId)
                    && !Objects.equals(existing.getUserId(), userId)) {
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED.getCode(), "无权访问该会话");
            }
            // 更新 last_message_at 和 message_count
            int newCount = (existing.getMessageCount() != null ? existing.getMessageCount() : 0) + 1;
            baseMapper.update(null,
                    new LambdaUpdateWrapper<ChatConversation>()
                            .eq(ChatConversation::getConversationId, conversationId)
                            .set(ChatConversation::getLastMessageAt, LocalDateTime.now())
                            .set(ChatConversation::getMessageCount, newCount));
            // update_time 由 DB 触发器 / MetaObjectHandler 维护，业务勿赋值
            existing.setLastMessageAt(LocalDateTime.now());
            existing.setMessageCount(newCount);
            return existing;
        }

        // 首次对话：插入新会话
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setAgentCode(agentCode);
        conversation.setUserId(userId);
        conversation.setMessageCount(1);
        conversation.setLastMessageAt(LocalDateTime.now());
        baseMapper.insert(conversation);
        log.info("ChatConversation created: conversationId={}, agentCode={}, userId={}",
                conversationId, agentCode, userId);
        return conversation;
    }

    /**
     * 按用户分页查询会话列表。
     *
     * @param userId   用户 ID
     * @param pageNum  页码
     * @param pageSize 页大小（最大 100）
     * @return 分页结果
     */
    @Override
    public IPage<ChatConversation> pageByUserId(String userId, long pageNum, long pageSize) {
        long pn = pageNum < 1 ? 1 : pageNum;
        long ps = pageSize < 1 ? 20 : Math.min(pageSize, 100);
        log.info("pageByUserId: userId={}, pageNum={}, pageSize={}", userId, pn, ps);
        return baseMapper.selectPage(
                new Page<>(pn, ps),
                new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, userId)
                        .eq(ChatConversation::getIsDel, 0)
                        .orderByDesc(ChatConversation::getLastMessageAt)
                        .orderByDesc(ChatConversation::getId));
    }

    /**
     * 校验会话存在且归属当前用户。
     *
     * @param conversationId 业务会话 ID
     * @param userId         用户 ID
     * @return 会话实体
     */
    @Override
    public ChatConversation requireOwned(String conversationId, String userId) {
        log.info("requireOwned: conversationId={}, userId={}", conversationId, userId);
        ChatConversation conversation = getByConversationId(conversationId);
        if (conversation == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(), "会话不存在");
        }
        if (!StringUtils.hasText(userId) || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED.getCode(), "无权访问该会话");
        }
        return conversation;
    }
}
