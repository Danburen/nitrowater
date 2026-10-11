package cn.nitrowater.resource.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Builds the vendor-neutral S3 client + presigner used by {@code S3FileStorage}.
 *
 * <p>Points at any S3-compatible endpoint (default: self-hosted SeaweedFS on port 8333) using
 * path-style access. Bucket creation is best-effort so the service still boots when the store is
 * unreachable at startup (the local backend stays usable); the error is logged.</p>
 */
@Slf4j
@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(ResourceProperties properties) {
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(properties.s3().endpoint()))
                .region(region(properties))
                .credentialsProvider(credentials(properties))
                .forcePathStyle(true)
                .build();
        ensureBucket(client, properties.s3().bucket());
        return client;
    }

    @Bean
    public S3Presigner s3Presigner(ResourceProperties properties) {
        ResourceProperties.S3 cfg = properties.s3();
        // Presigned URLs are handed to the browser, so they must use the browser-reachable endpoint
        // (e.g. the published host port / reverse-proxy domain), not the internal service hostname.
        String presignEndpoint = cfg.publicEndpoint() == null || cfg.publicEndpoint().isBlank()
                ? cfg.endpoint()
                : cfg.publicEndpoint();
        return S3Presigner.builder()
                .endpointOverride(URI.create(presignEndpoint))
                .region(region(properties))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static void ensureBucket(S3Client client, String bucket) {
        try {
            client.headBucket(b -> b.bucket(bucket));
        } catch (Exception headFailed) {
            try {
                client.createBucket(b -> b.bucket(bucket));
                log.info("Created S3 bucket '{}'", bucket);
            } catch (Exception createFailed) {
                log.warn("S3 bucket '{}' not reachable at startup: {}", bucket, createFailed.getMessage());
            }
        }
    }

    private static Region region(ResourceProperties properties) {
        String configured = properties.s3().region();
        return Region.of(configured == null || configured.isBlank() ? "us-east-1" : configured);
    }

    private static StaticCredentialsProvider credentials(ResourceProperties properties) {
        ResourceProperties.S3 cfg = properties.s3();
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey()));
    }
}
