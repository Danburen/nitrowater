package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Lifecycle status of a business user (mirrors account's status). */
@Getter
public enum UserStatus {
    ACTIVE(0),
    SUSPENDED(1);

    private final short value;

    UserStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static UserStatus fromValue(int value) {
        for (UserStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UserStatus value: " + value);
    }
}
