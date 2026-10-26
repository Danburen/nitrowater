package cn.nitrowater.lib.common.validation.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link UsernameValidator} — the {@code @Username} constraint on registration
 * and profile update payloads.
 *
 * <p>Contract (see {@code UsernameValidator}): a non-null value must match
 * {@code ^[a-zA-Z0-9_]+$} — ASCII letters, digits and underscore only, at least one character.
 * {@code null} is accepted (defers to {@code @NotBlank}, same lenient convention as
 * {@link PhoneNumberValidator}); {@code ""} is rejected because the regex requires 1+ chars —
 * that asymmetry is the current pinned contract.</p>
 */
class UsernameValidatorTest {

    private UsernameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UsernameValidator();
    }

    // ------------------------------------------------------------------
    // Equivalence class: accepted usernames ([a-zA-Z0-9_]+).
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "accepts [{0}]")
    @ValueSource(strings = {
            "alice",          // plain letters
            "Alice1",         // mixed case + digit
            "user_01",        // underscore + digits
            "0",              // single digit
            "_",              // single underscore
            "a",              // single letter
            "A1_b2_C3"        // all three character families
    })
    void acceptsUsernamesMatchingThePattern(String username) {
        assertTrue(validator.isValid(username, null));
    }

    // ------------------------------------------------------------------
    // Rejected: whitespace, punctuation, non-ASCII letters, and the empty
    // string (regex is `+`, not `*`, so "" fails even though null passes).
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(strings = {
            "",               // boundary: empty string (rejected despite null being accepted)
            " ",              // lone space
            "user name",      // embedded space
            "user-name",      // hyphen not allowed
            "user.name",      // dot not allowed
            "user@name",      // at-sign not allowed
            "user\n",         // line break not matched by the character class
            "\u7528\u6237\u540d",  // CJK characters are not [a-zA-Z0-9_]
            "\uFF41lice"      // full-width 'a' (U+FF41) is not ASCII a-z
    })
    void rejectsUsernamesOutsideThePattern(String username) {
        assertFalse(validator.isValid(username, null));
    }

    // ------------------------------------------------------------------
    // Regression pin: null used to NPE inside Pattern.matcher; the validator
    // now defers null to @NotBlank like its siblings.
    // ------------------------------------------------------------------

    @Test
    void nullDefersToNotBlankInsteadOfThrowing() {
        assertTrue(validator.isValid(null, null));
    }

    // ------------------------------------------------------------------
    // Robustness: oversized junk must be rejected, not throw.
    // ------------------------------------------------------------------

    @Test
    void rejectsOversizedInputWithoutThrowing() {
        String oversized = "a".repeat(10_000) + "!";
        assertFalse(validator.isValid(oversized, null));
    }

    @Test
    void initializeIsANoOp() {
        assertDoesNotThrow(() -> validator.initialize(null));
    }
}
