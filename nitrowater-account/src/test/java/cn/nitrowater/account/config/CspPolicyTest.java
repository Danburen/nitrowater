package cn.nitrowater.account.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hosted login form posts to /login and is then redirected to the client's (cross-origin)
 * callback; Chrome checks that redirect chain against {@code form-action}, so the configured
 * origins are security-relevant and must be validated strictly.
 */
class CspPolicyTest {

    @Test
    void keepsSelfOnlyWhenNoOriginsConfigured() {
        CspPolicy policy = new CspPolicy(List.of());

        assertTrue(policy.directives().startsWith("form-action 'self'; "));
        assertTrue(policy.directives().contains("frame-ancestors 'none'"));
    }

    @Test
    void skipsBlankEntries() {
        CspPolicy policy = new CspPolicy(List.of("", "   ", "http://localhost:5173"));

        assertEquals(1, occurrences(policy.directives(), "form-action"));
        assertTrue(policy.directives().contains("form-action 'self' http://localhost:5173;"));
    }

    @Test
    void appendsConfiguredOriginsInOrder() {
        CspPolicy policy = new CspPolicy(List.of("http://localhost:5173", "https://nitrowater.cn"));

        assertTrue(policy.directives()
                .contains("form-action 'self' http://localhost:5173 https://nitrowater.cn;"));
    }

    @Test
    void normalizesTrailingSlashHostCaseAndDefaultPort() {
        CspPolicy policy = new CspPolicy(List.of(
                "HTTPS://NitroWater.CN/",
                "https://nitrowater.cn:443",
                "http://localhost:80",
                "http://localhost:5173/"));

        assertTrue(policy.directives()
                .contains("form-action 'self' https://nitrowater.cn http://localhost http://localhost:5173;"));
    }

    @Test
    void rejectsWildcardOrigin() {
        assertThrows(IllegalArgumentException.class, () -> new CspPolicy(List.of("https://*.nitrowater.cn")));
    }

    @Test
    void rejectsOriginWithPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new CspPolicy(List.of("https://nitrowater.cn/login/oauth2/code/nitrowater")));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThrows(IllegalArgumentException.class, () -> new CspPolicy(List.of("javascript:alert(1)")));
        assertThrows(IllegalArgumentException.class, () -> new CspPolicy(List.of("ftp://localhost:5173")));
    }

    @Test
    void rejectsCommaJoinedValueThatWouldSmuggleEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> new CspPolicy(List.of("http://localhost:5173,http://evil.test")));
    }

    @Test
    void formActionAppearsExactlyOnce() {
        CspPolicy policy = new CspPolicy(List.of("https://nitrowater.cn"));

        assertEquals(1, occurrences(policy.directives(), "form-action"));
        assertFalse(policy.directives().contains("form-action 'self'; form-action"));
    }

    private static long occurrences(String text, String needle) {
        return text.split(needle, -1).length - 1L;
    }
}
