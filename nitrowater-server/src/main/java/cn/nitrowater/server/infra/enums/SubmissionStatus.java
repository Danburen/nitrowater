package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Processing status of a submission (judging lifecycle). */
@Getter
public enum SubmissionStatus {
    QUEUED(0),
    RUNNING(1),
    DONE(2),
    /** Judge-side system error (not the user's fault). */
    SE(3);

    private final short value;

    SubmissionStatus(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching status
     * @throws IllegalArgumentException when the value is unknown
     */
    public static SubmissionStatus fromValue(int value) {
        for (SubmissionStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SubmissionStatus value: " + value);
    }
}
