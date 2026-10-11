package cn.nitrowater.resource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the resource service ({@code app.resource.*}).
 *
 * @param defaultBackend backend used when a request does not specify one ({@code s3} / {@code cos} / {@code local})
 * @param presignExpirySeconds default lifetime of presigned upload/download URLs
 * @param maxSizeBytes maximum accepted object size
 * @param readCacheSafetyMarginSeconds TTL margin subtracted from a cached presigned GET URL so it is
 *        never served already-expired (defaults to {@code 60} when {@code null} or {@code <= 0})
 * @param s3 S3-compatible storage settings
 * @param cos Tencent COS storage settings (optional; the backend is only registered when credentials are set)
 * @param local local-filesystem backend settings
 */
@ConfigurationProperties(prefix = "app.resource")
public record ResourceProperties(
        String defaultBackend,
        int presignExpirySeconds,
        long maxSizeBytes,
        Integer readCacheSafetyMarginSeconds,
        S3 s3,
        Cos cos,
        Local local) {

    /** Default read-cache safety margin in seconds. */
    public static final int DEFAULT_CACHE_SAFETY_MARGIN_SECONDS = 60;

    /**
     * @return the effective read-cache safety margin: the configured value when positive, else
     *         {@link #DEFAULT_CACHE_SAFETY_MARGIN_SECONDS}
     */
    public int effectiveCacheSafetyMarginSeconds() {
        if (readCacheSafetyMarginSeconds == null || readCacheSafetyMarginSeconds <= 0) {
            return DEFAULT_CACHE_SAFETY_MARGIN_SECONDS;
        }
        // Never outlive the signature itself.
        return Math.min(readCacheSafetyMarginSeconds, Math.max(1, presignExpirySeconds - 1));
    }

    /**
     * S3-compatible storage settings (default: self-hosted SeaweedFS).
     *
     * @param endpoint S3 endpoint used server-side, e.g. {@code http://seaweedfs:8333}
     * @param accessKey access key
     * @param secretKey secret key
     * @param bucket target bucket name
     * @param region SigV4 region (e.g. {@code us-east-1}); blank uses the SDK default
     * @param publicEndpoint endpoint embedded in presigned URLs (must be browser-reachable); blank = endpoint
     */
    public record S3(String endpoint, String accessKey, String secretKey, String bucket, String region,
            String publicEndpoint) {
    }

    /**
     * Tencent COS storage settings. The backend is only registered when
     * {@code secret-id} and {@code secret-key} are both present.
     *
     * @param secretId Tencent Cloud secret id
     * @param secretKey Tencent Cloud secret key
     * @param region COS region, e.g. {@code ap-shanghai}
     * @param bucket COS bucket name (must carry the app id suffix, e.g. {@code mybucket-1250000000})
     */
    public record Cos(String secretId, String secretKey, String region, String bucket) {
    }

    /**
     * Local-filesystem backend settings.
     *
     * @param basePath root directory that object keys are resolved under
     */
    public record Local(String basePath) {
    }
}
