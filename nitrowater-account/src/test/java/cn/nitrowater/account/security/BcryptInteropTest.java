package cn.nitrowater.account.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the interop between the digest the ops script writes and the encoder that verifies logins.
 *
 * <p>The digest below was produced by {@code htpasswd -inBC 10} (apache2-utils / httpd), which is
 * what {@code deploy/bin/create-account.sh} calls; it emits the {@code $2y$} variant. The service
 * verifies with Spring's {@link BCryptPasswordEncoder}, so the two implementations - not just the
 * algorithm name - have to agree. If either side changes its prefix handling, cost parsing or byte
 * encoding, this test fails instead of shipping accounts nobody can sign in with.</p>
 */
class BcryptInteropTest {

    /** {@code printf '%s\n' 'ParityCheck1' | htpasswd -inBC 10 nitrowater} */
    private static final String HTPASSWD_DIGEST =
            "$2y$10$QvjFVeEf0zGe9oyh2XdEDuKxz5BANHx6N4r3WQOmN3IXVu28arBZu";

    private static final String PASSWORD = "ParityCheck1";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void acceptsTheDigestProducedByTheOpsScript() {
        assertThat(encoder.matches(PASSWORD, HTPASSWD_DIGEST)).isTrue();
    }

    @Test
    void rejectsAnyOtherPassword() {
        assertThat(encoder.matches("ParityCheck2", HTPASSWD_DIGEST)).isFalse();
    }

    @Test
    void writesDigestsTheSameToolCanVerify() {
        // The reverse direction: a digest this service would create must carry a prefix and cost the
        // script's shape check accepts, or a password reset done in the app would not be re-runnable
        // by the script (and vice versa).
        String digest = encoder.encode(PASSWORD);

        assertThat(digest).matches("^\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}$");
        assertThat(encoder.matches(PASSWORD, digest)).isTrue();
    }
}
