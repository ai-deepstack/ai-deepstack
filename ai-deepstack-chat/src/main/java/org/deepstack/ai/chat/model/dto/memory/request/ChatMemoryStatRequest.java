package org.deepstack.ai.chat.model.dto.memory.request;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 对话记忆统计请求
 *
 */
@Data
public class ChatMemoryStatRequest implements Serializable {

    /**
     * 起始日期（包含）
     */
    private LocalDate startDate;

    /**
     * 结束日期（包含）
     */
    private LocalDate endDate;
}
