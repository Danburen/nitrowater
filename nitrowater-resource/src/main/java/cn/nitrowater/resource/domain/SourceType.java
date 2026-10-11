package cn.nitrowater.resource.domain;

import lombok.Getter;

/** How a resource came to exist. */
@Getter
public enum SourceType {
    /** Provisioned by the system (seed/static asset). */
    SYSTEM(0),
    /** Attached to a piece of business content. */
    CONTENT_ATTACHED(1),
    /** Uploaded by an end user. */
    USER_UPLOADED(2);

    private final short value;

    SourceType(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching source type
     * @throws IllegalArgumentException when the value is unknown
     */
    public static SourceType fromValue(int value) {
        for (SourceType source : values()) {
            if (source.value == value) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown SourceType value: " + value);
    }
}
