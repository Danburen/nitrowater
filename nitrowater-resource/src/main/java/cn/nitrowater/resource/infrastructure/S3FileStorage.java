package cn.nitrowater.resource.infrastructure;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.domain.StorageBackend;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * S3-compatible storage backend (default: self-hosted SeaweedFS, via its S3 gateway).
 *
 * <p>Uses the AWS SDK v2 (vendor-neutral) to presign PUT/GET URLs, so clients transfer directly
 * and bytes stay off this service. Any S3-compatible endpoint (SeaweedFS, Garage, Ceph RGW, AWS S3)
 * can be targeted purely via {@code app.resource.s3.*} configuration; path-style access is enabled
 * for local stores.</p>
 */
@Component
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3FileStorage(S3Client client, S3Presigner presigner, ResourceProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = properties.s3().bucket();
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.S3;
    }

    @Override
    public String presignedPutUrl(String objectKey, String contentType, Duration expiry) {
        try {
            return presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .putObjectRequest(b -> b.bucket(bucket).key(objectKey))
                    .build()).url().toString();
        } catch (Exception ex) {
            throw new StorageException("Failed to presign PUT for " + objectKey, ex);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration expiry) {
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(b -> b.bucket(bucket).key(objectKey))
                    .build()).url().toString();
        } catch (Exception ex) {
            throw new StorageException("Failed to presign GET for " + objectKey, ex);
        }
    }

    @Override
    public void put(String objectKey, InputStream in, long size, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build(),
                    RequestBody.fromInputStream(in, size));
        } catch (Exception ex) {
            throw new StorageException("Failed to store object " + objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (Exception ex) {
            throw new StorageException("Failed to read object " + objectKey, ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            return ex.statusCode() != 404;
        }
    }

    @Override
    public long size(String objectKey) {
        try {
            HeadObjectResponse head = client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return head.contentLength();
        } catch (Exception ex) {
            throw new StorageException("Failed to stat object " + objectKey, ex);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            client.deleteObject(b -> b.bucket(bucket).key(objectKey));
        } catch (Exception ex) {
            throw new StorageException("Failed to remove object " + objectKey, ex);
        }
    }
}
