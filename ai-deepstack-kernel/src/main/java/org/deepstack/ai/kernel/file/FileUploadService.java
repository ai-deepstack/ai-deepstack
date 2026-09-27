package org.deepstack.ai.kernel.file;

import org.deepstack.ai.kernel.file.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传（业务层）。
 */
public interface FileUploadService {

    /**
     * 上传文件到对象存储。
     *
     * @param file    上传文件
     * @param bizType 业务目录（如 agent-covers、avatars），用于组织对象键前缀
     * @return 上传结果
     */
    FileUploadResponse upload(MultipartFile file, String bizType);
}
