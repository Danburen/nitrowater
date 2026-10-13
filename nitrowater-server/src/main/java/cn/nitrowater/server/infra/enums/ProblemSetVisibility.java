package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Visibility of a (user-owned) problem set. */
@Getter
public enum ProblemSetVisibility {
    PRIVATE(0),
    PUBLIC(1);

    private final short value;

    ProblemSetVisibility(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching visibility
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ProblemSetVisibility fromValue(int value) {
        for (ProblemSetVisibility visibility : values()) {
            if (visibility.value == value) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown ProblemSetVisibility value: " + value);
    }
}
