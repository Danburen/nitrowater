package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Self-declared experience level, used to tune explanation depth. */
@Getter
public enum Expertise {
    /** Needs step-by-step guidance and definitions. */
    BEGINNER(0),
    /** Default: knows the basics, needs the specifics. */
    INTERMEDIATE(1),
    /** Wants concise, precise answers without basics. */
    EXPERT(2);

    private final short value;

    Expertise(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching level
     * @throws IllegalArgumentException when the value is unknown
     */
    public static Expertise fromValue(int value) {
        for (Expertise expertise : values()) {
            if (expertise.value == value) {
                return expertise;
            }
        }
        throw new IllegalArgumentException("Unknown Expertise value: " + value);
    }
}
