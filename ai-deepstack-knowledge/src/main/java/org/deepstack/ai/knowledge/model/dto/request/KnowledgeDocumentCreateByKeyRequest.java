package org.deepstack.ai.knowledge.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文档按 OSS Key 创建请求
 * <p>
 * 文件上传后，将 fileKey 和元数据通过此 DTO 传给 AI 服务，
 * AI 服务不接收 multipart 文件，只按 key 从 OSS 下载并解析。
 * </p>
 *
 */
@Data
public class KnowledgeDocumentCreateByKeyRequest implements Serializable {

    @NotNull(message = "知识库 id 不能为空")
    private Long knowledgeBaseId;

    @NotBlank(message = "fileKey 不能为空")
    @Size(max = 512, message = "fileKey 长度不能超过 512")
    private String fileKey;

    /**
     * 文件访问 URL（OSS 公开或预签名 URL，用于回查）
     */
    @Size(max = 1024, message = "filePath 长度不能超过 1024")
    private String filePath;

    @NotBlank(message = "原始文件名不能为空")
    @Size(max = 256, message = "文件名长度不能超过 256")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @Size(max = 64, message = "mimeType 长度不能超过 64")
    private String mimeType;

    /**
     * 文档标题（可选，缺省取文件名）
     */
    @Size(max = 256, message = "文档标题长度不能超过 256")
    private String title;

    /**
     * 逗号分隔标签
     */
    @Size(max = 256, message = "标签长度不能超过 256")
    private String tags;
}
