package org.deepstack.ai.knowledge.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文档新建请求（手工录入 / URL 抓取）
 * <p>
 * 文件上传走单独 multipart 接口，不使用本 DTO。
 * </p>
 *
 */
@Data
public class KnowledgeDocumentCreateRequest implements Serializable {

    @NotNull(message = "知识库 id 不能为空")
    private Long knowledgeBaseId;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 256, message = "文档标题长度不能超过 256")
    private String title;

    /**
     * 来源类型：MANUAL / URL（FILE 走 multipart 接口）
     */
    @NotBlank(message = "来源类型不能为空")
    @Pattern(regexp = "MANUAL|URL", message = "来源类型仅支持 MANUAL / URL")
    private Integer sourceType;

    /**
     * URL（source_type=URL 时必填）
     */
    @Size(max = 1024, message = "URL 长度不能超过 1024")
    private String sourceUrl;

    /**
     * 手工录入正文（source_type=MANUAL 时必填）
     */
    private String rawContent;

    /**
     * 逗号分隔标签
     */
    @Size(max = 256, message = "标签长度不能超过 256")
    private String tags;
}
