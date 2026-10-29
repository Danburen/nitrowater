package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Who changed a NitroBot profile field. */
@Getter
public enum ProfileChangeSource {
    /** Explicitly set by the user. */
    USER(0),
    /** Inferred from conversation content; always carries a confidence. */
    INFERRED(1);

    private final short value;

    ProfileChangeSource(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching source
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ProfileChangeSource fromValue(int value) {
        for (ProfileChangeSource source : values()) {
            if (source.value == value) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown ProfileChangeSource value: " + value);
    }
}
