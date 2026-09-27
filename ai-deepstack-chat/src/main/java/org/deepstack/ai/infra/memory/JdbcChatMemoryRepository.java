package org.deepstack.ai.infra.memory;

import org.deepstack.ai.chat.model.entity.memory.ChatMemoryRecord;
import org.deepstack.ai.chat.mapper.memory.ChatMemoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 JDBC (MyBatis-Plus) 的 ChatMemoryRepository 实现
 * <p>
 * 对话记忆持久化到 PostgreSQL，服务重启后不丢失。
 * Message 序列化策略：message_type (user/assistant/system) + content，丢失 metadata。
 * </p>
 * <p>
 * {@link #saveAll} 与 Spring AI {@code MessageWindowChatMemory} 对齐：每次传入的是
 * <b>当前窗口内的完整消息列表</b>（非整轮增量）。本实现按内容后缀匹配后仅追加新增尾部，
 * 避免按列表全量插入导致多轮重复落库；窗口裁剪掉的旧消息仍保留在库中以便审计。
 * </p>
 *
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMemoryMapper chatMemoryMapper;

    /**
     * 列出库中已有记忆的全部 conversationId。
     */
    @Override
    public List<String> findConversationIds() {
        log.info("findConversationIds");
        List<String> ids = chatMemoryMapper.selectList(
                        new LambdaQueryWrapper<ChatMemoryRecord>()
                                .select(ChatMemoryRecord::getConversationId)
                                .groupBy(ChatMemoryRecord::getConversationId)
                ).stream()
                .map(ChatMemoryRecord::getConversationId)
                .toList();
        log.info("findConversationIds 完成: count={}", ids.size());
        return ids;
    }

    /**
     * 按会话 ID 加载全部记忆消息（按 seq 升序）。
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        log.info("findByConversationId: conversationId={}", conversationId);
        List<ChatMemoryRecord> records = chatMemoryMapper.selectList(
                new LambdaQueryWrapper<ChatMemoryRecord>()
                        .eq(ChatMemoryRecord::getConversationId, conversationId)
                        .orderByAsc(ChatMemoryRecord::getSeq)
        );
        return records.stream()
                .map(this::toMessage)
                .toList();
    }

    /**
     * 追加写入当前记忆窗口中尚未落库的尾部消息（后缀对齐，非整表覆盖）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(String conversationId, List<Message> messages) {
        log.info("saveAll: conversationId={}, messageCount={}",
                conversationId, messages != null ? messages.size() : 0);
        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<ChatMemoryRecord> existing = chatMemoryMapper.selectList(
                new LambdaQueryWrapper<ChatMemoryRecord>()
                        .eq(ChatMemoryRecord::getConversationId, conversationId)
                        .orderByAsc(ChatMemoryRecord::getSeq)
        );

        int overlap = findPrefixOverlap(existing, messages);
        if (overlap >= messages.size()) {
            // 传入窗口已全部存在于库尾部，无需写入
            log.debug("saveAll 跳过: 窗口已全部落库 conversationId={}, size={}", conversationId, messages.size());
            return;
        }

        List<Message> toAppend = messages.subList(overlap, messages.size());
        int startSeq = existing.isEmpty()
                ? 0
                : (existing.get(existing.size() - 1).getSeq() != null
                ? existing.get(existing.size() - 1).getSeq() + 1
                : existing.size());

        List<ChatMemoryRecord> records = new ArrayList<>(toAppend.size());
        for (int i = 0; i < toAppend.size(); i++) {
            Message msg = toAppend.get(i);
            ChatMemoryRecord record = new ChatMemoryRecord();
            record.setConversationId(conversationId);
            record.setMessageType(msg.getMessageType().getValue());
            record.setContent(msg.getText());
            record.setSeq(startSeq + i);
            // create_time 由 DB DEFAULT / MetaObjectHandler 填充，业务勿赋值
            records.add(record);
        }
        try {
            chatMemoryMapper.insertBatch(records);
            log.info("ChatMemory append: conversationId={}, existing={}, overlap={}, appended={}",
                    conversationId, existing.size(), overlap, toAppend.size());
        } catch (Exception e) {
            log.error("ChatMemory saveAll 失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 计算 {@code messages} 前缀与 {@code existing} 后缀的最长对齐长度。
     * <p>
     * Spring AI 每次传入当前记忆窗口。常见情况：
     * <ul>
     *   <li>窗口增长：existing 的后缀 == messages 的前缀，需追加尾部新消息</li>
     *   <li>窗口裁剪：messages 仅保留最近 N 条，仍是 existing 的某一后缀的前缀延伸</li>
     * </ul>
     * 若无法对齐（例如内容被外部改写），退化为 overlap=0，即全量追加传入列表。
     * </p>
     */
    private static int findPrefixOverlap(List<ChatMemoryRecord> existing, List<Message> messages) {
        if (existing.isEmpty()) {
            return 0;
        }
        int max = Math.min(existing.size(), messages.size());
        for (int overlap = max; overlap > 0; overlap--) {
            boolean match = true;
            for (int i = 0; i < overlap; i++) {
                ChatMemoryRecord rec = existing.get(existing.size() - overlap + i);
                Message msg = messages.get(i);
                if (!Objects.equals(rec.getMessageType(), msg.getMessageType().getValue())
                        || !Objects.equals(nullToEmpty(rec.getContent()), nullToEmpty(msg.getText()))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return overlap;
            }
        }
        return 0;
    }

    /** null 转空串，便于内容对齐比较。 */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 按会话 ID 物理删除全部记忆记录。
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        log.info("deleteByConversationId: conversationId={}", conversationId);
        int rows = chatMemoryMapper.delete(
                new LambdaQueryWrapper<ChatMemoryRecord>()
                        .eq(ChatMemoryRecord::getConversationId, conversationId)
        );
        log.info("deleteByConversationId 完成: conversationId={}, deleted={}", conversationId, rows);
    }

    /** 将 DB 记录还原为 Spring AI Message。 */
    private Message toMessage(ChatMemoryRecord record) {
        return switch (record.getMessageType()) {
            case "user" -> new UserMessage(record.getContent());
            case "assistant" -> new AssistantMessage(record.getContent());
            case "system" -> new SystemMessage(record.getContent() != null ? record.getContent() : "");
            default -> {
                log.warn("未知 message_type={}, 按 user 处理: conversationId={}",
                        record.getMessageType(), record.getConversationId());
                yield new UserMessage(record.getContent() != null ? record.getContent() : "");
            }
        };
    }
}
