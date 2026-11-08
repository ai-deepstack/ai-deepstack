package org.deepstack.ai.storage;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
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
    private final S3Presigner presigner;
    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();

    public S3FileService(DeepstackS3Properties props) {
        this.props = props;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey()));
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(props.isPathStyleAccess())
                .build();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
        // 预签名包含 host，必须用公网地址签，否则前端拿到的 URL 无法访问
        String signEndpoint = StringUtils.hasText(props.getPublicEndpoint())
                ? props.getPublicEndpoint()
                : props.getEndpoint();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(signEndpoint))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
        log.info("S3FileService ready: endpoint={}, publicEndpoint={}, region={}, pathStyle={}, presignExpiry={}, defaultBucket={}",
                props.getEndpoint(), props.getPublicEndpoint(), props.getRegion(),
                props.isPathStyleAccess(), effectivePresignExpiry(), props.getDefaultBucket());
    }

    /** 释放客户端连接。 */
    @PreDestroy
    public void close() {
        s3Client.close();
        presigner.close();
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
            String stableUrl = buildObjectUrl(targetBucket, fileKey);
            String presignedUrl = presign(targetBucket, fileKey);
            FileInfo info = new FileInfo();
            info.setFileName(fileKey);
            info.setFileUrl(stableUrl);
            info.setUrl(presignedUrl);
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

    /** 返回 FileUrl（预签名，默认桶）。 */
    @Override
    public String getFileUrl(String objectKey) {
        return presign(props.getDefaultBucket(), objectKey);
    }

    /** 将本存储的对象 URL 转成预签名 URL；外部 URL 原样返回。 */
    @Override
    public String presignUrl(String url) {
        String[] bucketKey = parseOwnUrl(url);
        return bucketKey == null ? url : presign(bucketKey[0], bucketKey[1]);
    }

    /** 去掉签名参数，恢复成可长期存储的稳定 URL；外部 URL 原样返回。 */
    @Override
    public String normalizeUrl(String url) {
        String[] bucketKey = parseOwnUrl(url);
        return bucketKey == null ? url : buildObjectUrl(bucketKey[0], bucketKey[1]);
    }

    /** 解析本存储的 path-style 对象 URL（忽略签名参数）；非本存储返回 null。 */
    private String[] parseOwnUrl(String url) {
        if (!StringUtils.hasText(url) || !props.isPathStyleAccess()) {
            return null;
        }
        String noQuery = url.split("\\?", 2)[0];
        for (String base : new String[]{props.getPublicEndpoint(), props.getEndpoint()}) {
            if (!StringUtils.hasText(base)) {
                continue;
            }
            String prefix = base.replaceAll("/+$", "") + "/";
            if (!noQuery.startsWith(prefix)) {
                continue;
            }
            String rest = noQuery.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash <= 0 || slash == rest.length() - 1) {
                continue;
            }
            return new String[]{rest.substring(0, slash), rest.substring(slash + 1)};
        }
        return null;
    }

    /** 生成 GET 预签名 URL（有效期见 {@code deepstack.s3.presign-expiry}）。 */
    private String presign(String bucket, String key) {
        PresignedGetObjectRequest req = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(effectivePresignExpiry())
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(resolveBucket(bucket))
                                .key(key)
                                .build())
                        .build());
        return req.url().toString();
    }

    /** 预签名有效期兜底：必须为正且不超过 SigV4 的 7 天上限。 */
    private Duration effectivePresignExpiry() {
        Duration configured = props.getPresignExpiry();
        Duration max = Duration.ofDays(7);
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return max;
        }
        return configured.compareTo(max) > 0 ? max : configured;
    }

    /** 解析实际桶名。 */
    private String resolveBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return props.getDefaultBucket();
        }
        return bucket;
    }

    /** 拼对象访问 URL（优先使用公网地址，未配置时回退到 endpoint）。 */
    private String buildObjectUrl(String bucket, String key) {
        String base = StringUtils.hasText(props.getPublicEndpoint())
                ? props.getPublicEndpoint()
                : props.getEndpoint();
        base = base.replaceAll("/+$", "");
        if (props.isPathStyleAccess()) {
            return base + "/" + bucket + "/" + key;
        }
        // virtual-hosted：http://bucket.host/...
        String withoutScheme = base.replaceFirst("^https?://", "");
        String scheme = base.startsWith("https") ? "https://" : "http://";
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
