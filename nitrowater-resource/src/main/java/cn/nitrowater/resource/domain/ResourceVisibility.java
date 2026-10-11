package cn.nitrowater.resource.domain;

import lombok.Getter;

/** Access visibility of a resource. */
@Getter
public enum ResourceVisibility {
    /** Only authorized callers may read. */
    PRIVATE(0),
    /** Anyone (including anonymous) may read via the public endpoint. */
    PUBLIC(1);

    private final short value;

    ResourceVisibility(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching visibility
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ResourceVisibility fromValue(int value) {
        for (ResourceVisibility visibility : values()) {
            if (visibility.value == value) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown ResourceVisibility value: " + value);
    }
}
