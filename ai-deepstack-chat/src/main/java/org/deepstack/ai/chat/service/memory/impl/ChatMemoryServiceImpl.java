package org.deepstack.ai.chat.service.memory.impl;

import org.deepstack.ai.chat.model.dto.memory.request.ChatMemoryStatRequest;
import org.deepstack.ai.chat.model.dto.memory.response.ChatMemoryStatResponse;
import org.deepstack.ai.chat.model.entity.memory.ChatMemoryRecord;
import org.deepstack.ai.chat.mapper.memory.ChatMemoryMapper;
import org.deepstack.ai.chat.service.memory.ChatMemoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话记忆统计服务实现
 * <p>
 * 提供统计与物理删除；不在此接口暴露完整对话内容。
 * chat_memory 表无 is_del 字段，删除为物理 DELETE。
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private final ChatMemoryMapper chatMemoryMapper;

    /**
     * 统计记忆消息量（按日期 / 类型聚合）。
     *
     * @param req 统计条件（起止日期）
     * @return 统计结果
     */
    @Override
    public ChatMemoryStatResponse stat(ChatMemoryStatRequest req) {
        log.info("stat: startDate={}, endDate={}", req.getStartDate(), req.getEndDate());
        ChatMemoryStatResponse resp = new ChatMemoryStatResponse();

        LambdaQueryWrapper<ChatMemoryRecord> wrapper = new LambdaQueryWrapper<>();
        if (req.getStartDate() != null) {
            wrapper.ge(ChatMemoryRecord::getCreateTime, req.getStartDate().atStartOfDay());
        }
        if (req.getEndDate() != null) {
            wrapper.le(ChatMemoryRecord::getCreateTime, req.getEndDate().atTime(LocalTime.MAX));
        }

        // 总消息数
        Long totalMessages = chatMemoryMapper.selectCount(wrapper);
        resp.setTotalMessages(totalMessages);

        // 总会话数（按 conversationId 去重）—— 用 QueryWrapper 做自定义 select
        QueryWrapper<ChatMemoryRecord> convWrapper = new QueryWrapper<>();
        convWrapper.select("COUNT(DISTINCT conversation_id) AS totalConversations");
        if (req.getStartDate() != null) {
            convWrapper.ge("create_time", req.getStartDate().atStartOfDay());
        }
        if (req.getEndDate() != null) {
            convWrapper.le("create_time", req.getEndDate().atTime(LocalTime.MAX));
        }
        List<Map<String, Object>> convResult = chatMemoryMapper.selectMaps(convWrapper);
        long totalConversations = convResult.isEmpty() ? 0L
                : ((Number) convResult.get(0).get("totalconversations")).longValue();
        resp.setTotalConversations(totalConversations);

        // 按日期聚合
        resp.setByDate(statByDate(req.getStartDate(), req.getEndDate()));

        // 按消息类型聚合
        resp.setByType(statByType(req.getStartDate(), req.getEndDate()));

        log.info("stat 完成: totalMessages={}, totalConversations={}", totalMessages, totalConversations);
        return resp;
    }

    /**
     * 按会话 ID 物理删除记忆。
     *
     * @param conversationId 业务会话 ID
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        log.info("deleteByConversationId: conversationId={}", conversationId);
        chatMemoryMapper.delete(
                new LambdaQueryWrapper<ChatMemoryRecord>()
                        .eq(ChatMemoryRecord::getConversationId, conversationId)
        );
        log.info("Deleted chat memory by conversationId={}", conversationId);
    }

    /**
     * 物理删除指定日期之前的记忆。
     *
     * @param date 日期字符串（yyyy-MM-dd），早于该日 00:00 的记录将被删除
     */
    @Override
    public void deleteBefore(String date) {
        log.info("deleteBefore: date={}", date);
        LocalDate localDate = LocalDate.parse(date);
        LocalDateTime threshold = localDate.atStartOfDay();
        chatMemoryMapper.delete(
                new LambdaQueryWrapper<ChatMemoryRecord>()
                        .lt(ChatMemoryRecord::getCreateTime, threshold)
        );
        log.info("Deleted chat memory before {}", date);
    }

    /**
     * 按会话拉取 user/assistant 消息（按 seq 升序）。
     *
     * @param conversationId 业务会话 ID
     * @return 消息列表
     */
    @Override
    public List<ChatMemoryRecord> listMessagesForClient(String conversationId) {
        log.info("listMessagesForClient: conversationId={}", conversationId);
        List<ChatMemoryRecord> list = chatMemoryMapper.selectList(
                new LambdaQueryWrapper<ChatMemoryRecord>()
                        .eq(ChatMemoryRecord::getConversationId, conversationId)
                        .in(ChatMemoryRecord::getMessageType, List.of("user", "assistant"))
                        .orderByAsc(ChatMemoryRecord::getSeq)
                        .orderByAsc(ChatMemoryRecord::getId));
        if (list.isEmpty()) {
            log.warn("会话记忆为空: conversationId={}", conversationId);
        }
        return list;
    }

    // ===== 私有辅助方法 =====

    /** 按日期聚合消息条数。 */
    private List<ChatMemoryStatResponse.DateCount> statByDate(LocalDate startDate, LocalDate endDate) {
        QueryWrapper<ChatMemoryRecord> wrapper = new QueryWrapper<>();
        wrapper.select("TO_CHAR(create_time, 'YYYY-MM-DD') AS date", "COUNT(*) AS count")
                .groupBy("TO_CHAR(create_time, 'YYYY-MM-DD')")
                .orderByAsc("TO_CHAR(create_time, 'YYYY-MM-DD')");
        if (startDate != null) {
            wrapper.ge("create_time", startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le("create_time", endDate.atTime(LocalTime.MAX));
        }

        List<Map<String, Object>> rows = chatMemoryMapper.selectMaps(wrapper);
        List<ChatMemoryStatResponse.DateCount> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ChatMemoryStatResponse.DateCount dc = new ChatMemoryStatResponse.DateCount();
            dc.setDate((String) row.get("date"));
            dc.setCount(((Number) row.get("count")).longValue());
            result.add(dc);
        }
        return result;
    }

    /** 按消息类型聚合条数。 */
    private List<ChatMemoryStatResponse.TypeCount> statByType(LocalDate startDate, LocalDate endDate) {
        QueryWrapper<ChatMemoryRecord> wrapper = new QueryWrapper<>();
        wrapper.select("message_type AS type", "COUNT(*) AS count")
                .groupBy("message_type")
                .orderByDesc("count");
        if (startDate != null) {
            wrapper.ge("create_time", startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le("create_time", endDate.atTime(LocalTime.MAX));
        }

        List<Map<String, Object>> rows = chatMemoryMapper.selectMaps(wrapper);
        List<ChatMemoryStatResponse.TypeCount> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ChatMemoryStatResponse.TypeCount tc = new ChatMemoryStatResponse.TypeCount();
            tc.setType((String) row.get("type"));
            tc.setCount(((Number) row.get("count")).longValue());
            result.add(tc);
        }
        return result;
    }
}
