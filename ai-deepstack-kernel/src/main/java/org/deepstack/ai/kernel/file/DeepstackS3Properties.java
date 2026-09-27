package org.deepstack.ai.kernel.file;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 协议对象存储配置（AWS S3 及任意兼容实现）。
 * <p>自建 / 兼容端点通常需开启 {@code path-style-access=true}。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "deepstack.s3")
public class DeepstackS3Properties {

    /** Endpoint，如 http://127.0.0.1:9000 或云厂商提供的 S3 地址 */
    private String endpoint = "http://127.0.0.1:9000";

    /** 区域（兼容实现常用 us-east-1） */
    private String region = "us-east-1";

    private String accessKeyId = "deepstack";

    private String secretAccessKey = "deepstack123";

    /** 未指定 bucket 时使用 */
    private String defaultBucket = "deepstack";

    /**
     * 路径风格访问：true → http://endpoint/bucket/key（多数兼容端点推荐）；
     * false → http://bucket.endpoint/key（部分云厂商默认）
     */
    private boolean pathStyleAccess = true;
}
