package cn.nitrowater.resource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the resource service ({@code app.resource.*}).
 *
 * @param defaultBackend backend used when a request does not specify one ({@code s3} / {@code local})
 * @param presignExpirySeconds default lifetime of presigned upload/download URLs
 * @param maxSizeBytes maximum accepted object size
 * @param s3 S3-compatible storage settings
 * @param local local-filesystem backend settings
 */
@ConfigurationProperties(prefix = "app.resource")
public record ResourceProperties(
        String defaultBackend,
        int presignExpirySeconds,
        long maxSizeBytes,
        S3 s3,
        Local local) {

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
     * Local-filesystem backend settings.
     *
     * @param basePath root directory that object keys are resolved under
     */
    public record Local(String basePath) {
    }
}
