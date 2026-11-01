package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Content-moderation posture for the assistant, persisted in {@code ai_admin_setting}.
 *
 * <p>Rollout is deliberately staged: start at {@link #LOG} to measure the real hit and
 * false-positive rate, then switch to {@link #ENFORCE} once the dictionary and the allow list are
 * tuned. {@link #OFF} keeps the whole gate out of the hot path.</p>
 */
@Getter
public enum ModerationMode {
    /** No matching at all. */
    OFF(0),
    /** Shadow mode: hits are recorded for review, nothing is blocked or masked. */
    LOG(1),
    /** Enforce: flagged questions are rejected and answers are masked before they are stored. */
    ENFORCE(2);

    private final short value;

    ModerationMode(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching mode
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ModerationMode fromValue(int value) {
        for (ModerationMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown ModerationMode value: " + value);
    }

    /** @return whether matching should run at all */
    public boolean active() {
        return this != OFF;
    }

    /** @return whether hits should actually block / mask instead of only being recorded */
    public boolean enforcing() {
        return this == ENFORCE;
    }
}
