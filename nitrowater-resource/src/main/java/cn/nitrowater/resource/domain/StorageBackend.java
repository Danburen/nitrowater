package cn.nitrowater.resource.domain;

import lombok.Getter;

/** Storage backend that physically holds the bytes of a resource. */
@Getter
public enum StorageBackend {
    S3(0),
    COS(1),
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
