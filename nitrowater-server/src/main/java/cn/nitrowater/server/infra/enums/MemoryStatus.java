package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Lifecycle state of a long-term memory entry. */
@Getter
public enum MemoryStatus {
    /** Eligible for prompt injection. */
    ACTIVE(0),
    /** Deleted by the user; kept for audit only. */
    USER_DELETED(1),
    /** Aged out by retention rules. */
    EXPIRED(2);

    private final short value;

    MemoryStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static MemoryStatus fromValue(int value) {
        for (MemoryStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown MemoryStatus value: " + value);
    }
}
