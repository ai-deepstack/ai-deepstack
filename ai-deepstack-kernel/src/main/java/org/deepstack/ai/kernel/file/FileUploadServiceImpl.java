package org.deepstack.ai.kernel.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.kernel.file.dto.FileUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 通用文件上传实现：按 bizType 分目录写入对象存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Pattern BIZ_TYPE = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");
    private static final String DEFAULT_BIZ_TYPE = "common";

    private final FileService fileService;

    /**
     * 上传 multipart 文件到对象存储（按 bizType/日期分目录）。
     *
     * @param file    上传文件（≤20MB）
     * @param bizType 业务目录；空白则用 common
     * @return 含 url / fileKey 的响应
     */
    @Override
    public FileUploadResponse upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("文件不能超过 20MB");
        }

        String dir = normalizeBizType(bizType);
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            original = "file";
        }
        String safeName = original.replaceAll("[\\\\/:*?\"<>|]", "_");
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        String ext = extOf(safeName, contentType);
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileKey = dir + "/" + dateDir + "/"
                + UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            FileInfo info = fileService.uploadToBucket(
                    file.getInputStream(),
                    fileKey,
                    contentType,
                    file.getSize(),
                    null
            );
            String url = info.getUrl() != null ? info.getUrl() : info.getFileUrl();
            log.info("File uploaded: bizType={}, key={}, size={}", dir, fileKey, file.getSize());

            FileUploadResponse resp = new FileUploadResponse();
            resp.setUrl(url != null ? url : "");
            resp.setFileKey(fileKey);
            resp.setOriginalFilename(original);
            resp.setContentType(contentType);
            resp.setSize(file.getSize());
            return resp;
        } catch (IOException e) {
            log.error("File upload failed: bizType={}, name={}", dir, original, e);
            throw new IllegalStateException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 规范化 bizType；非法字符抛参数异常。
     *
     * @param bizType 原始业务类型
     * @return 合法目录名
     */
    private static String normalizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return DEFAULT_BIZ_TYPE;
        }
        String trimmed = bizType.trim();
        if (!BIZ_TYPE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("bizType 仅允许字母、数字、下划线、中划线");
        }
        return trimmed;
    }

    /**
     * 从文件名或 Content-Type 推断扩展名。
     *
     * @param name        原始文件名
     * @param contentType MIME 类型
     * @return 含点的扩展名，无法推断时为空串
     */
    private static String extOf(String name, String contentType) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String ext = name.substring(dot).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.[a-z0-9]{1,10}")) {
                return ext;
            }
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return switch (ct) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}
