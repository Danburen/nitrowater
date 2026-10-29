package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Author role of a persisted NitroBot message. */
@Getter
public enum MessageRole {
    /** Question submitted by the user. */
    USER(0),
    /** Answer produced by the model. */
    ASSISTANT(1),
    /** Failed turn (rate limit, upstream error) kept so the user sees the failure later. */
    ERROR(2);

    private final short value;

    MessageRole(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching role
     * @throws IllegalArgumentException when the value is unknown
     */
    public static MessageRole fromValue(int value) {
        for (MessageRole role : values()) {
            if (role.value == value) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown MessageRole value: " + value);
    }
}
