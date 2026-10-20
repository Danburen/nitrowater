package cn.nitrowater.lib.common.validation.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StrongPasswordValidator} — the password policy used by registration.
 *
 * <p>Policy (see {@code StrongPasswordValidator.PASSWORD_PATTERN}):
 * {@code ^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,20}$} — at least one lowercase, one uppercase and one
 * digit, total length 8..20.</p>
 *
 * <p>The tests are deliberately data-driven: one parameterized case per equivalence class plus the
 * two length boundaries (7/8 and 20/21), and the documented null/empty contract (this validator
 * stays permissive and leaves those to {@code @NotBlank}, so the annotation pair is what actually
 * rejects a blank password).</p>
 */
class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
    }

    // ------------------------------------------------------------------
    // Equivalence class: valid input (all three character classes, 8..20)
    // Includes the two accepted boundaries: exactly 8 and exactly 20.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "accepts [{0}]")
    @ValueSource(strings = {
            "Abcdefg1",            // boundary: 8 chars (min accepted)
            "Abcdefghijklmnopqr1s", // boundary: 20 chars (max accepted)
            "Abcdef1!",             // symbols do not disqualify
            "Aa1bbbbbbbbbbbbbbbbb"  // 20 chars, repeated filler
    })
    void acceptsStrongPasswords(String password) {
        assertTrue(validator.isValid(password, null));
    }

    // ------------------------------------------------------------------
    // Equivalence classes: one missing character class each,
    // plus the rejected length boundaries (7 and 21) and malformed input.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(strings = {
            "Abcdef1",             // boundary: 7 chars (too short)
            "Abcdefghijklmnopqr1st", // boundary: 21 chars (too long)
            "ABCDEFG1",            // missing lowercase
            "abcdefg1",            // missing uppercase
            "Abcdefgh",            // missing digit
            "12345678",            // digits only
            "        ",            // 8 spaces: no character class
            "Abcdef1\n",           // line break is not matched by '.'
            "\uFF21bcdefg1"        // full-width 'A' (U+FF21) is not [A-Z]
    })
    void rejectsPasswordsOutsideThePolicy(String password) {
        assertFalse(validator.isValid(password, null));
    }

    // ------------------------------------------------------------------
    // Documented contract: null/empty are NOT this validator's job — it returns true
    // so that @NotBlank / @Size report the violation. Covers both operands of the
    // `value == null || value.isEmpty()` decision (short-circuit and full evaluation).
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "defers null/empty to other constraints: [{0}]")
    @NullAndEmptySource
    void defersNullAndEmptyToOtherConstraints(String password) {
        assertTrue(validator.isValid(password, null));
    }

    // ------------------------------------------------------------------
    // Robustness: oversized input must be rejected, not throw.
    // ------------------------------------------------------------------

    @Test
    void rejectsOversizedInputWithoutThrowing() {
        String oversized = "A1" + "a".repeat(10_000);
        assertFalse(validator.isValid(oversized, null));
    }

    @Test
    void initializeIsANoOp() {
        assertDoesNotThrow(() -> validator.initialize(null));
    }
}
