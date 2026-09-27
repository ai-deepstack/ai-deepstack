package org.deepstack.ai.agent.model.dto.userprompt.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户全域提示词管理响应
 *
 */
@Data
public class UserPromptResponse implements Serializable {

    private Long id;

    /**
     * 用户 id
     */
    private String userId;

    /**
     * 提示词内容
     */
    private String promptContent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
