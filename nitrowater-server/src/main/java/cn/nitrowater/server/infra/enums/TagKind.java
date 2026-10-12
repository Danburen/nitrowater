package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Grouping of a problem tag (LeetCode-style: topic / company).
 *
 * <p>Curated collections ("problem sets") are a separate concept — see {@code problem_set}.</p>
 *
 * <p>Codes are <b>append-only</b>: never renumber an existing code (it is persisted).</p>
 */
@Getter
public enum TagKind {
    /** Algorithm topic, e.g. array / dynamic-programming. */
    TOPIC(0),
    /** Company tag, e.g. a company that asked this problem in interviews. */
    COMPANY(1);

    private final short value;

    TagKind(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching kind
     * @throws IllegalArgumentException when the value is unknown
     */
    public static TagKind fromValue(int value) {
        for (TagKind kind : values()) {
            if (kind.value == value) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown TagKind value: " + value);
    }
}
