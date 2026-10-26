package cn.nitrowater.account.config;

/**
 * Helpers for the hosted login page's optional {@code redirect} parameter.
 *
 * <p>Only same-origin absolute paths are accepted. This deliberately rejects protocol-relative
 * values ({@code //evil.example}), backslash-normalised values ({@code /\evil.example}), schemes
 * ({@code https://evil.example}) and control characters, while still allowing the admin console's
 * hash routes (for example {@code /admin/#/users}).</p>
 */
final class LoginRedirects {

    /** Form/query parameter carrying the post-login target. */
    static final String PARAM = "redirect";

    private LoginRedirects() {
    }

    /**
     * Returns {@code value} when it is a safe same-origin path, otherwise {@code null}.
     *
     * @param value raw query/form value, may be {@code null}
     * @return a path beginning with a single {@code /}, or {@code null} when unsafe
     */
    static String safeLocalPath(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("/")) {
            return null;
        }
        // "//host" is protocol-relative; "/\host" is normalised to it by some browsers.
        if (trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' || c == '\r' || c == '\n' || c < 0x20) {
                return null;
            }
        }
        return trimmed;
    }
}
