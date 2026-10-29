package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Category of a long-term NitroBot memory entry. */
@Getter
public enum MemoryKind {
    /** Stable fact about the user or their environment. */
    FACT(0),
    /** Stated preference (language, style, tooling). */
    PREFERENCE(1),
    /** A decision the user made that should not be re-litigated. */
    DECISION(2),
    /** Ongoing project context. */
    PROJECT(3);

    private final short value;

    MemoryKind(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching kind
     * @throws IllegalArgumentException when the value is unknown
     */
    public static MemoryKind fromValue(int value) {
        for (MemoryKind kind : values()) {
            if (kind.value == value) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown MemoryKind value: " + value);
    }
}
