package org.deepstack.ai.kernel.file.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用文件上传结果。
 */
@Data
public class FileUploadResponse implements Serializable {

    /** 可访问 URL */
    private String url;

    /** 对象存储中的文件 key */
    private String fileKey;

    /** 原始文件名 */
    private String originalFilename;

    /** MIME 类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long size;
}
