package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/** Preferred answer length for NitroBot replies. */
@Getter
public enum ReplyStyle {
    /** Short answers, minimal prose. */
    CONCISE(0),
    /** Default: explanations with the necessary steps. */
    BALANCED(1),
    /** Detailed explanations with background and examples. */
    DETAILED(2);

    private final short value;

    ReplyStyle(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching style
     * @throws IllegalArgumentException when the value is unknown
     */
    public static ReplyStyle fromValue(int value) {
        for (ReplyStyle style : values()) {
            if (style.value == value) {
                return style;
            }
        }
        throw new IllegalArgumentException("Unknown ReplyStyle value: " + value);
    }
}
