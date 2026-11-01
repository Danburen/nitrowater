package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** What the moderation gate did with a hit. */
@Getter
public enum ModerationAction {
    /** Shadow mode: recorded only. */
    LOGGED(0),
    /** The turn was rejected before it reached the model. */
    BLOCKED(1),
    /** The answer was stored and returned with the matched terms masked. */
    MASKED(2);

    private final short value;

    ModerationAction(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching action
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ModerationAction fromValue(int value) {
        for (ModerationAction action : values()) {
            if (action.value == value) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown ModerationAction value: " + value);
    }
}
