package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Lifecycle status of a problem editorial.
 *
 * <p>{@link #HIDDEN} is used instead of a soft-delete flag. Codes are <b>append-only</b>:
 * never renumber an existing code (it is persisted).</p>
 */
@Getter
public enum EditorialStatus {
    /** Draft — admin only. */
    DRAFT(0),
    /** Published — publicly visible to everyone. */
    PUBLISHED(1),
    /** Hidden — withdrawn from public view, admin only. */
    HIDDEN(2);

    private final short value;

    EditorialStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static EditorialStatus fromValue(int value) {
        for (EditorialStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown EditorialStatus value: " + value);
    }
}
