package org.deepstack.ai.chat.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话历史消息（仅 user / assistant）。
 */
@Data
public class ChatMessageResponse implements Serializable {

    /** 角色：user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** 消息时间 */
    private LocalDateTime createTime;
}
