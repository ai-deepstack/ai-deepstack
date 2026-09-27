package org.deepstack.ai.chat.model.dto.memory.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 对话记忆（短期 ChatMemory）统计响应
 *
 */
@Data
public class ChatMemoryStatResponse implements Serializable {

    private Long totalMessages;

    private Long totalConversations;

    private List<DateCount> byDate;

    private List<TypeCount> byType;

    @Data
    public static class DateCount implements Serializable {
        private String date;
        private Long count;
    }

    @Data
    public static class TypeCount implements Serializable {
        private String type;
        private Long count;
    }
}
