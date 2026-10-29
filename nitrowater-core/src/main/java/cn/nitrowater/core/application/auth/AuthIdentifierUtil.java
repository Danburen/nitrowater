package cn.nitrowater.core.application.auth;

import cn.nitrowater.core.api.auth.VerifyChannel;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes and classifies the identifiers accepted by login and registration.
 *
 * <p>Resolution is format-based on purpose. A legacy implementation tried phone, then email,
 * then username for every input, which could authenticate the wrong account when one user's
 * username happened to equal another user's phone number. Classifying the shape first makes
 * the lookup deterministic and lets registration reject ambiguous usernames up front.</p>
 */
public final class AuthIdentifierUtil {

    /** Mainland China mobile number, matching {@code PhoneNumberValidator}. */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** Deliberately permissive: the authoritative check is Jakarta's {@code @Email} at the DTO. */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private AuthIdentifierUtil() {
    }

    /**
     * Canonical form used before hashing an email, so {@code Foo@Example.com} and
     * {@code foo@example.com} resolve to the same account.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Canonical form for a phone number (surrounding whitespace only). */
    public static String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }

    /** @return whether {@code value} has the shape of an email address */
    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value.trim()).matches();
    }

    /** @return whether {@code value} has the shape of a mainland mobile number */
    public static boolean isPhone(String value) {
        return value != null && PHONE.matcher(value.trim()).matches();
    }

    /**
     * Detects which verification channel a quick-login identifier belongs to.
     *
     * @param identifier phone number or email address
     * @return {@link VerifyChannel#EMAIL} for an email, {@link VerifyChannel#SMS} for a phone
     * @throws IllegalArgumentException when the value is neither (the controller maps this to a
     *                                  stable "target unsupported" auth error)
     */
    public static VerifyChannel detectChannel(String identifier) {
        if (isEmail(identifier)) {
            return VerifyChannel.EMAIL;
        }
        if (isPhone(identifier)) {
            return VerifyChannel.SMS;
        }
        throw new IllegalArgumentException("Identifier is neither a phone nor an email: " + identifier);
    }
}
