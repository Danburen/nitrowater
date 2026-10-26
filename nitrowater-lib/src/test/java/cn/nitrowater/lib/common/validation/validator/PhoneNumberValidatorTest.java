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
 * Unit tests for {@link PhoneNumberValidator} — the {@code @PhoneNumber} constraint used on
 * account registration and profile update payloads.
 *
 * <p>Contract (see {@code PhoneNumberValidator}): {@code null} or empty means "field not
 * provided" and is accepted (lenient, defers to {@code @NotBlank} when the field is mandatory);
 * any other value must match {@code ^1[3-9]\d{9}$} — a mainland-China mobile number of exactly
 * 11 digits whose second digit is 3..9.</p>
 *
 * <p>Coverage intent: equivalence classes (valid prefix range / invalid prefix, wrong length,
 * embedded separators, non-digit noise, full-width digits), the null/empty deferral branch, and
 * the no-op {@code initialize}.</p>
 */
class PhoneNumberValidatorTest {

    private PhoneNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PhoneNumberValidator();
    }

    // ------------------------------------------------------------------
    // Equivalence class: accepted mobile numbers.
    // Second digit spans the whole [3-9] range at the lower edge of length.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "accepts [{0}]")
    @ValueSource(strings = {
            "13800138000", // classic 13x
            "13912345678", // 13x upper edge
            "15012345678", // 15x
            "16612345678", // 16x
            "17012345678", // 17x lower edge
            "19912345678"  // 19x upper edge
    })
    void acceptsWellFormedMobileNumbers(String phone) {
        assertTrue(validator.isValid(phone, null));
    }

    // ------------------------------------------------------------------
    // Rejected: second digit outside [3-9], wrong length, separator/noise
    // padding, and full-width digits (Java \d is ASCII-only).
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(strings = {
            "12800138000",        // second digit 2 (not a mobile prefix)
            "10000138000",        // second digit 0
            "23800138000",        // does not start with 1
            "1380013800",         // boundary: 10 digits (too short)
            "138001380001",       // boundary: 12 digits (too long)
            "138-0013-8000",      // separators are not stripped
            "138 0013 8000",      // embedded spaces
            " 13800138000",       // leading space
            "13800138000 ",       // trailing space
            "1380013800a",        // trailing letter
            "abcdefghijk",        // no digits at all
            "+8613800138000",     // country-code prefix is not normalized
            "\uFF11\uFF33\uFF1800138000" // full-width digits do not match ASCII \d
    })
    void rejectsNumbersOutsideThePattern(String phone) {
        assertFalse(validator.isValid(phone, null));
    }

    // ------------------------------------------------------------------
    // Documented contract: null/empty are NOT this validator's job — it
    // returns true so that @NotBlank reports the violation. Covers both
    // operands of the `s == null || s.isEmpty()` decision.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "defers null/empty to other constraints: [{0}]")
    @NullAndEmptySource
    void defersNullAndEmptyToOtherConstraints(String phone) {
        assertTrue(validator.isValid(phone, null));
    }

    // ------------------------------------------------------------------
    // Robustness: oversized junk must be rejected, not throw.
    // ------------------------------------------------------------------

    @Test
    void rejectsOversizedInputWithoutThrowing() {
        String oversized = "1" + "3".repeat(10_000);
        assertFalse(validator.isValid(oversized, null));
    }

    @Test
    void initializeIsANoOp() {
        assertDoesNotThrow(() -> validator.initialize(null));
    }
}
