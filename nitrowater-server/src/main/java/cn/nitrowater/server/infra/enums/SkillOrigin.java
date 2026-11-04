package cn.nitrowater.server.infra.enums;

/**
 * How a managed skill row came to exist.
 */
public enum SkillOrigin {

    /** Written in the console form. */
    FORM((short) 0),
    /** Uploaded as a {@code SKILL.md} file. */
    IMPORT((short) 1);

    private final short value;

    SkillOrigin(short value) {
        this.value = value;
    }

    /** @return the persisted integer code */
    public short getValue() {
        return value;
    }

    /**
     * @param value persisted integer code
     * @return the matching origin
     * @throws IllegalArgumentException when the code is unknown
     */
    public static SkillOrigin fromValue(int value) {
        for (SkillOrigin origin : values()) {
            if (origin.value == value) {
                return origin;
            }
        }
        throw new IllegalArgumentException("Unknown SkillOrigin code: " + value);
    }
}
