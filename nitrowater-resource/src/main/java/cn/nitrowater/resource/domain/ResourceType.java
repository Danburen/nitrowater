package cn.nitrowater.resource.domain;

import lombok.Getter;

/** Coarse category of a resource, used for allow-listing and display. */
@Getter
public enum ResourceType {
    UNKNOWN(0),
    IMAGE(1),
    VIDEO(2),
    AUDIO(3),
    TEXT(4),
    DOCUMENT(5),
    ARCHIVE(6),
    EXECUTABLE(7),
    OTHER(99);

    private final short value;

    ResourceType(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching resource type
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ResourceType fromValue(int value) {
        for (ResourceType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ResourceType value: " + value);
    }
}
