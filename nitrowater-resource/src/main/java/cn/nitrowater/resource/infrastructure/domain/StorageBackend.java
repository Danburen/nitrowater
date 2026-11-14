package cn.nitrowater.resource.infrastructure.domain;

import lombok.Getter;

/**
 * Storage backend that physically holds the bytes of a resource.
 *
 * <p>Only the local filesystem (default) and Tencent COS are supported. Persisted codes are stable:
 * {@code 0} belonged to the retired S3 backend and must never be reused (the baseline migration
 * {@code V1__resource_baseline.sql} documents the retirement and defaults the column to {@code 2}).</p>
 */
@Getter
public enum StorageBackend {
    /** Tencent COS object storage (optional; only registered when credentials are configured). */
    COS(1),
    /** Local filesystem under {@code app.resource.local.base-path} (default). */
    LOCAL(2);

    private final short value;

    StorageBackend(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching backend
     * @throws IllegalArgumentException when the value is unknown
     */
    public static StorageBackend fromValue(int value) {
        for (StorageBackend backend : values()) {
            if (backend.value == value) {
                return backend;
            }
        }
        throw new IllegalArgumentException("Unknown StorageBackend value: " + value);
    }
}
