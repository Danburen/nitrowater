package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Lifecycle state of a NitroBot conversation. */
@Getter
public enum ConversationStatus {
    /** Visible in the conversation list. */
    ACTIVE(0),
    /** Hidden from the default list, still readable. */
    ARCHIVED(1),
    /** Soft-deleted; messages stay until purged. */
    DELETED(2);

    private final short value;

    ConversationStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ConversationStatus fromValue(int value) {
        for (ConversationStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ConversationStatus value: " + value);
    }
}
