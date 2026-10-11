package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Input/output judging mode of a problem. */
@Getter
public enum IoMode {
    /** Standard I/O (read stdin, write stdout). */
    ACM(0),
    /** LeetCode-style method-call harness. */
    LEETCODE(1);

    private final short value;

    IoMode(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching mode
     * @throws IllegalArgumentException when the value is unknown
     */
    public static IoMode fromValue(int value) {
        for (IoMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown IoMode value: " + value);
    }
}
