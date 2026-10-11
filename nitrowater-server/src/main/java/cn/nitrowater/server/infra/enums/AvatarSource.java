package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Origin of a business user's avatar snapshot. */
@Getter
public enum AvatarSource {
    /** Mirror account's canonical avatar (refreshed on login). */
    ACCOUNT(0),
    /** Overridden locally by this application. */
    LOCAL(1);

    private final short value;

    AvatarSource(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching source
     * @throws IllegalArgumentException when the value is unknown
     */
    public static AvatarSource fromValue(int value) {
        for (AvatarSource source : values()) {
            if (source.value == value) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown AvatarSource value: " + value);
    }
}
