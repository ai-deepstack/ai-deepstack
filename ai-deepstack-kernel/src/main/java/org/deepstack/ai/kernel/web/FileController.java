package org.deepstack.ai.kernel.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.kernel.file.FileUploadService;
import org.deepstack.ai.kernel.file.dto.FileUploadResponse;
import org.deepstack.ai.kernel.model.Response;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传 API，各业务共用。
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;

    /**
     * 上传文件到对象存储。
     *
     * @param file    文件
     * @param bizType 业务目录前缀（可选，默认 common），如 agent-covers、avatars
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false, defaultValue = "common") String bizType) {
        return Response.success(fileUploadService.upload(file, bizType));
    }
}
