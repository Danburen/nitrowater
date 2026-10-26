package cn.nitrowater.lib.utils.codec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Base62} — the URL-safe alphabet used by short codes
 * ({@code 0-9 A-Z a-z}, same digit order as the implementation constant).
 *
 * <p>Vectors were computed with an independent reference implementation of the
 * divide-by-62 algorithm; the Long.MAX_VALUE case pins the widest input the
 * single-{@code long} API can receive.</p>
 */
class Base62Test {

    // ------------------------------------------------------------------
    // Known vectors: single digits, alphabet edges (9/A/z), base boundary
    // (61 -> z, 62 -> 10), and progressively larger magnitudes.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "encode({0}) == \"{1}\"")
    @CsvSource({
            "0, 0",
            "1, 1",
            "9, 9",
            "10, A",
            "35, Z",
            "36, a",
            "61, z",
            "62, 10",
            "123, 1z",
            "12345, 3D7",
            "123456789, 8M0kX",
            "16777215, 18OWF",
            "9223372036854775807, AzL8n0Y58m7"
    })
    void encodesKnownVectors(long input, String expected) {
        assertEquals(expected, Base62.encode(input));
    }

    // ------------------------------------------------------------------
    // Degenerate contract: negatives never enter the division loop, so the
    // empty builder collapses to "0" instead of throwing.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "encode({0}) degenerates to \"0\"")
    @ValueSource(longs = {-1L, -62L, Long.MIN_VALUE})
    void negativeInputsDegenerateToZero(long input) {
        assertEquals("0", Base62.encode(input));
    }

    // ------------------------------------------------------------------
    // Alphabet invariant: every produced code uses only the 62 charset chars.
    // ------------------------------------------------------------------

    @Test
    void encodesStayWithinTheAlphabet() {
        for (long i = 0; i < 500; i++) {
            assertTrue(Base62.encode(i).matches("[0-9A-Za-z]+"), "encode(" + i + ")");
        }
    }
}
