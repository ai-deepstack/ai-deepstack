package org.deepstack.ai.chat.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话历史分页查询请求。
 */
@Data
public class ChatConversationPageRequest implements Serializable {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 20;

    /** 按 Agent 过滤；空表示全部 */
    private String agentCode;
}
