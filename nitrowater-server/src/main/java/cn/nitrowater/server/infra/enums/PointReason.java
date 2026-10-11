package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Reason for a point-log entry. */
@Getter
public enum PointReason {
    SOLVE(0),
    CONTEST(1),
    SIGN_IN(2),
    ADMIN(3);

    private final short value;

    PointReason(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching reason
     * @throws IllegalArgumentException when the value is unknown
     */
    public static PointReason fromValue(int value) {
        for (PointReason reason : values()) {
            if (reason.value == value) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown PointReason value: " + value);
    }
}
