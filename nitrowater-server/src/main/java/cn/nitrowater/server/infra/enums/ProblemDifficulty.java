package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Difficulty rating of a problem. */
@Getter
public enum ProblemDifficulty {
    EASY(0),
    MEDIUM(1),
    HARD(2);

    private final short value;

    ProblemDifficulty(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching difficulty
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ProblemDifficulty fromValue(int value) {
        for (ProblemDifficulty difficulty : values()) {
            if (difficulty.value == value) {
                return difficulty;
            }
        }
        throw new IllegalArgumentException("Unknown ProblemDifficulty value: " + value);
    }
}
