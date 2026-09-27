package org.deepstack.ai.knowledge.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文档修改请求（标题 / 标签 / 正文，禁止修改来源类型与归属库）
 *
 * <p>修改 {@code rawContent} 后业务层应清空旧 chunks 并重新触发 embedding。</p>
 *
 */
@Data
public class KnowledgeDocumentUpdateRequest implements Serializable {

    @NotNull(message = "文档 id 不能为空")
    private Long id;

    @Size(max = 256, message = "文档标题长度不能超过 256")
    private String title;

    /**
     * 修改后的正文（仅 MANUAL 类型支持）
     */
    private String rawContent;

    @Size(max = 256, message = "标签长度不能超过 256")
    private String tags;
}
