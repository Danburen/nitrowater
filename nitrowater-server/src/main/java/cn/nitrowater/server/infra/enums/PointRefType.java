package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Type of the entity a point-log entry references. */
@Getter
public enum PointRefType {
    PROBLEM(0),
    SUBMISSION(1),
    CONTEST(2);

    private final short value;

    PointRefType(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching type
     * @throws IllegalArgumentException when the value is unknown
     */
    public static PointRefType fromValue(int value) {
        for (PointRefType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PointRefType value: " + value);
    }
}
