package org.deepstack.ai.kernel.file;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 S3 协议的文件存储（AWS SDK，兼容各类 S3 端点）。
 */
@Slf4j
@Service
@EnableConfigurationProperties(DeepstackS3Properties.class)
public class S3FileService implements FileService {

    private final DeepstackS3Properties props;
    private final S3Client s3Client;
    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();

    public S3FileService(DeepstackS3Properties props) {
        this.props = props;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyleAccess())
                        .build())
                .build();
        log.info("S3FileService ready: endpoint={}, region={}, pathStyle={}, defaultBucket={}",
                props.getEndpoint(), props.getRegion(), props.isPathStyleAccess(), props.getDefaultBucket());
    }

    /** 释放客户端连接。 */
    @PreDestroy
    public void close() {
        s3Client.close();
    }

    /** 上传对象到指定桶。 */
    @Override
    public FileInfo uploadToBucket(InputStream inputStream, String fileKey, String contentType,
                                   long contentLength, String bucket) {
        String targetBucket = resolveBucket(bucket);
        ensureBucket(targetBucket);
        try {
            PutObjectRequest.Builder req = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(fileKey)
                    .contentType(contentType);
            if (contentLength > 0) {
                req.contentLength(contentLength);
                s3Client.putObject(req.build(), RequestBody.fromInputStream(inputStream, contentLength));
            } else {
                byte[] bytes = inputStream.readAllBytes();
                s3Client.putObject(req.build(), RequestBody.fromBytes(bytes));
                contentLength = bytes.length;
            }
            String url = buildObjectUrl(targetBucket, fileKey);
            FileInfo info = new FileInfo();
            info.setFileName(fileKey);
            info.setFileUrl(url);
            info.setUrl(url);
            info.setContentType(contentType);
            info.setSize(contentLength);
            log.info("S3 upload ok: bucket={}, key={}, size={}", targetBucket, fileKey, contentLength);
            return info;
        } catch (Exception e) {
            log.error("S3 upload failed: bucket={}, key={}", targetBucket, fileKey, e);
            throw new IllegalStateException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    /** 下载对象流。 */
    @Override
    public InputStream download(String bucket, String objectKey) {
        String targetBucket = resolveBucket(bucket);
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("S3 download failed: bucket={}, key={}", targetBucket, objectKey, e);
            throw new IllegalStateException("S3 download failed: " + e.getMessage(), e);
        }
    }

    /** 删除对象。 */
    @Override
    public void delete(String bucket, String objectKey) {
        String targetBucket = resolveBucket(bucket);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .build());
            log.info("S3 delete ok: bucket={}, key={}", targetBucket, objectKey);
        } catch (Exception e) {
            log.error("S3 delete failed: bucket={}, key={}", targetBucket, objectKey, e);
            throw new IllegalStateException("S3 delete failed: " + e.getMessage(), e);
        }
    }

    /** 返回 FileUrl。 */
    @Override
    public String getFileUrl(String objectKey) {
        return buildObjectUrl(props.getDefaultBucket(), objectKey);
    }

    /** 解析实际桶名。 */
    private String resolveBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return props.getDefaultBucket();
        }
        return bucket;
    }

    /** 拼对象访问 URL。 */
    private String buildObjectUrl(String bucket, String key) {
        String endpoint = props.getEndpoint().replaceAll("/$", "");
        if (props.isPathStyleAccess()) {
            return endpoint + "/" + bucket + "/" + key;
        }
        // virtual-hosted：http://bucket.host/...
        String withoutScheme = endpoint.replaceFirst("^https?://", "");
        String scheme = endpoint.startsWith("https") ? "https://" : "http://";
        return scheme + bucket + "." + withoutScheme + "/" + key;
    }

    /** 桶不存在则创建。 */
    private void ensureBucket(String bucket) {
        if (!ensuredBuckets.add(bucket)) {
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            createBucketQuietly(bucket);
        } catch (S3Exception e) {
            // 部分兼容端点对不存在桶返回 404 / 403
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                createBucketQuietly(bucket);
            } else {
                ensuredBuckets.remove(bucket);
                throw e;
            }
        }
    }

    /** 创建桶，已存在时忽略。 */
    private void createBucketQuietly(String bucket) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("S3 bucket created: {}", bucket);
        } catch (S3Exception ex) {
            // 并发创建时可能已存在
            log.warn("S3 createBucket {}: {}", bucket, ex.getMessage());
        }
    }
}
