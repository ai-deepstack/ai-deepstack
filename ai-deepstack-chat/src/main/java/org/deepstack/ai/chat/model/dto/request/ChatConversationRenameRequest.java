package org.deepstack.ai.chat.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话重命名请求。
 */
@Data
public class ChatConversationRenameRequest implements Serializable {

    /** 新标题（1-256 字） */
    @Size(max = 256, message = "标题长度不能超过 256")
    private String title;
}
