package cn.nitrowater.server.infra.enums;

import lombok.Getter;

/**
 * Judge verdict. {@code NULL} in the DB means "not yet judged" (unlike AC).
 *
 * <p>Mirrors Piston's execution status plus output comparison (see the judge design).</p>
 */
@Getter
public enum Verdict {
    /** Accepted. */
    AC(0),
    /** Wrong answer (stdout mismatch). */
    WA(1),
    /** Time limit exceeded. */
    TLE(2),
    /** Memory limit exceeded. */
    MLE(3),
    /** Runtime error. */
    RE(4),
    /** Compile error. */
    CE(5),
    /** Output limit exceeded. */
    OLE(6),
    /** Judge-side system error. */
    SE(7);

    private final short value;

    Verdict(int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted tinyint value
     * @return the matching verdict
     * @throws IllegalArgumentException when the value is unknown
     */
    public static Verdict fromValue(int value) {
        for (Verdict verdict : values()) {
            if (verdict.value == value) {
                return verdict;
            }
        }
        throw new IllegalArgumentException("Unknown Verdict value: " + value);
    }
}
