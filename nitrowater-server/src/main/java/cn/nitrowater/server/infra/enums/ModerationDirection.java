package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Where a moderation hit was observed.
 *
 * <p>The direction is what makes review meaningful: input hits are the user's own words and are
 * the only ones that count towards risk control; output and memory hits are recorded for tuning
 * and never penalise the account.</p>
 */
@Getter
public enum ModerationDirection {
    /** The user's question. */
    INPUT(0),
    /** The model's answer (may also contain quoted document or web text). */
    OUTPUT(1),
    /** A memory the model tried to save for later turns. */
    MEMORY(2),
    /**
     * Free-form text the user stored in their own profile ({@code ai_user_profile.preferences}).
     * It is re-injected into every later turn, so it gets the same treatment as a memory.
     */
    PROFILE(3);

    private final short value;

    ModerationDirection(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching direction
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ModerationDirection fromValue(int value) {
        for (ModerationDirection direction : values()) {
            if (direction.value == value) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown ModerationDirection value: " + value);
    }
}
