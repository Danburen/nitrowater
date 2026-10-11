package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** A user's aggregate status for one problem. */
@Getter
public enum ProblemStatus {
    /** Tried but not solved yet. */
    ATTEMPTED(0),
    /** Solved (at least one AC). */
    SOLVED(1);

    private final short value;

    ProblemStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ProblemStatus fromValue(int value) {
        for (ProblemStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ProblemStatus value: " + value);
    }
}
