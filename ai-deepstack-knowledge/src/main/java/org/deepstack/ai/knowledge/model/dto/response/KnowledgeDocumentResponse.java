package org.deepstack.ai.knowledge.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档管理响应
 *
 * <p>{@code rawContent} 不在列表中返回，详情接口单独提供。</p>
 *
 */
@Data
public class KnowledgeDocumentResponse implements Serializable {

    private Long id;

    private Long knowledgeBaseId;

    private String knowledgeBaseName;

    private String title;

    private Integer sourceType;

    /** 来源类型中文名 */
    private String sourceTypeName;

    private String sourceUrl;

    private String fileName;

    private Long fileSize;

    private String mimeType;

    /**
     * 详情接口才返回，列表接口为 null
     */
    private String rawContent;

    private String tags;

    private Integer parseStatus;

    private String parseStatusName;

    private Integer embedStatus;

    private String embedStatusName;

    /** 写图状态码 */
    private Integer graphStatus;

    /** 写图状态中文名 */
    private String graphStatusName;

    /** 写图失败原因 */
    private String graphError;

    private Integer chunkCount;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
