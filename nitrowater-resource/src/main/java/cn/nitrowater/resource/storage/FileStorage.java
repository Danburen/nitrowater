package cn.nitrowater.resource.storage;

import cn.nitrowater.resource.domain.StorageBackend;

import java.io.InputStream;
import java.time.Duration;

/**
 * A pluggable byte-storage backend.
 *
 * <p>Backends that support S3-style presigning return non-null URLs from {@link #presignedPutUrl}
 * and {@link #presignedGetUrl} so clients can transfer directly; backends that do not (e.g. the
 * local filesystem) return {@code null} and callers fall back to proxying through this service.</p>
 */
public interface FileStorage {

    /** @return the backend this implementation serves */
    StorageBackend backend();

    /**
     * @param objectKey storage key
     * @param contentType content type the client will send on PUT
     * @param expiry URL lifetime
     * @return a presigned PUT URL, or {@code null} when presigning is unsupported
     */
    String presignedPutUrl(String objectKey, String contentType, Duration expiry);

    /**
     * @param objectKey storage key
     * @param expiry URL lifetime
     * @return a presigned GET URL, or {@code null} when presigning is unsupported
     */
    String presignedGetUrl(String objectKey, Duration expiry);

    /**
     * Stores bytes server-side.
     *
     * @param objectKey storage key
     * @param in content stream (consumed, not closed by the caller)
     * @param size content length in bytes ({@code -1} if unknown)
     * @param contentType content type
     */
    void put(String objectKey, InputStream in, long size, String contentType);

    /** @return a stream over the stored object (caller closes it) */
    InputStream get(String objectKey);

    /** @return whether the object exists */
    boolean exists(String objectKey);

    /** @return object size in bytes */
    long size(String objectKey);

    /** Deletes the object if present. */
    void remove(String objectKey);
}
