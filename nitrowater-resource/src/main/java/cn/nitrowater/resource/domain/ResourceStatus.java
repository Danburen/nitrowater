package cn.nitrowater.resource.domain;

import lombok.Getter;

/** Lifecycle status of a resource record. */
@Getter
public enum ResourceStatus {
    /** Registered but not yet uploaded/validated. */
    PENDING(0),
    /** Uploaded and validated; usable. */
    ACTIVE(1),
    /** Referenced nowhere (reaped by cleanup). */
    ORPHAN(2),
    /** Soft-deleted. */
    DELETED(3);

    private final short value;

    ResourceStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ResourceStatus fromValue(int value) {
        for (ResourceStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ResourceStatus value: " + value);
    }
}
