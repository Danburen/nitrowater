package cn.nitrowater.resource.service;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.infrastructure.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.exception.StorageException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Tencent COS storage backend.
 *
 * <p>Optional: only registered when the COS credentials and bucket are configured (see
 * {@link cn.nitrowater.resource.config.CosClientConfig} for why the condition tests the values,
 * not just the presence of the keys). The backend can presign PUT/GET URLs with the bucket's public
 * {@code *.myqcloud.com} endpoint, but this deployment keeps {@code app.resource.presign-enabled=false}
 * by default so every read and write still goes through this service (single public origin) and no
 * browser-reachable bucket host is required.</p>
 */
@Component
@ConditionalOnExpression("'${app.resource.cos.secret-id:}' != ''"
        + " and '${app.resource.cos.secret-key:}' != ''"
        + " and '${app.resource.cos.bucket:}' != ''")
public class CosFileStorageService implements FileStorageService {

    private final COSClient cosClient;
    private final String bucket;

    public CosFileStorageService(COSClient cosClient, ResourceProperties properties) {
        this.cosClient = cosClient;
        this.bucket = properties.cos().bucket();
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.COS;
    }

    @Override
    public String presignedPutUrl(String objectKey, String contentType, Duration expiry) {
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethodName.PUT);
            request.setExpiration(Date.from(Instant.now().plus(expiry)));
            return cosClient.generatePresignedUrl(request).toString();
        } catch (Exception ex) {
            throw new StorageException("Failed to presign COS PUT for " + objectKey, ex);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration expiry) {
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethodName.GET);
            request.setExpiration(Date.from(Instant.now().plus(expiry)));
            return cosClient.generatePresignedUrl(request).toString();
        } catch (Exception ex) {
            throw new StorageException("Failed to presign COS GET for " + objectKey, ex);
        }
    }

    @Override
    public void put(String objectKey, InputStream in, long size, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null) {
                metadata.setContentType(contentType);
            }
            cosClient.putObject(new PutObjectRequest(bucket, objectKey, in, metadata));
        } catch (Exception ex) {
            throw new StorageException("Failed to store COS object " + objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            COSObject object = cosClient.getObject(bucket, objectKey);
            return object.getObjectContent();
        } catch (Exception ex) {
            throw new StorageException("Failed to read COS object " + objectKey, ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            return cosClient.doesObjectExist(bucket, objectKey);
        } catch (Exception ex) {
            throw new StorageException("Failed to stat COS object " + objectKey, ex);
        }
    }

    @Override
    public long size(String objectKey) {
        try {
            return cosClient.getObjectMetadata(bucket, objectKey).getContentLength();
        } catch (Exception ex) {
            throw new StorageException("Failed to stat COS object " + objectKey, ex);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            cosClient.deleteObject(bucket, objectKey);
        } catch (Exception ex) {
            throw new StorageException("Failed to remove COS object " + objectKey, ex);
        }
    }
}
