package org.deepstack.ai.storage;

import java.io.InputStream;

/**
 * 文件存储 SPI（S3 协议）。实现见 {@link S3FileService}。
 */
public interface FileService {

    /**
     * 上传文件到指定 bucket。
     *
     * @param inputStream   文件流
     * @param fileKey       对象键
     * @param contentType   MIME 类型
     * @param contentLength 内容长度（字节）；未知可传 0
     * @param bucket        存储桶（可空，回落默认桶）
     * @return 上传后的文件元信息
     */
    FileInfo uploadToBucket(InputStream inputStream, String fileKey, String contentType, long contentLength, String bucket);

    /**
     * 下载对象流。
     *
     * @param bucket    存储桶（可空，回落默认桶）
     * @param objectKey 对象键
     * @return 输入流（调用方负责关闭）
     */
    InputStream download(String bucket, String objectKey);

    /**
     * 删除对象。
     *
     * @param bucket    存储桶（可空，回落默认桶）
     * @param objectKey 对象键
     */
    void delete(String bucket, String objectKey);

    /**
     * 根据对象键拼装可访问 URL。
     *
     * @param objectKey 对象键
     * @return URL
     */
    default String getFileUrl(String objectKey) {
        return objectKey;
    }

    /**
     * 将对象 URL 转成可访问的预签名 URL；非本存储 URL 原样返回。
     */
    default String presignUrl(String url) {
        return url;
    }

    /**
     * 去掉预签名参数，恢复成可长期存储的稳定 URL；非本存储 URL 原样返回。
     */
    default String normalizeUrl(String url) {
        return url;
    }
}
