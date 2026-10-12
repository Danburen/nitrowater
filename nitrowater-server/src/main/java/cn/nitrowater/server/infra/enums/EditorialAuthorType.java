package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Author type of a problem editorial.
 *
 * <p>Codes are <b>append-only</b>: never renumber an existing code (it is persisted).</p>
 */
@Getter
public enum EditorialAuthorType {
    /** Official editorial written by the problem setter / admin. */
    OFFICIAL(0),
    /** User-submitted editorial (requires a prior AC; reserved for a later phase). */
    USER(1);

    private final short value;

    EditorialAuthorType(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching author type
     * @throws IllegalArgumentException when the value is unknown
     */
    public static EditorialAuthorType fromValue(int value) {
        for (EditorialAuthorType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EditorialAuthorType value: " + value);
    }
}
