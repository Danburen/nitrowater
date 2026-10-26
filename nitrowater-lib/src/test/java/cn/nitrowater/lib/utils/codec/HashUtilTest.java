package cn.nitrowater.lib.utils.codec;

import cn.nitrowater.lib.utils.DataAdapter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HashUtil} — HMAC/SHA-256/MD5 helpers, random salting and the
 * Snowflake-backed short-code generator.
 *
 * <p>Known-answer vectors (RFC-style, cross-checked with Python's {@code hmac}/{@code hashlib}):
 * HMAC-SHA256("The quick brown fox jumps over the lazy dog", key="key"),
 * SHA-256("abc"), MD5("abc"), and hashWithSalt("password","salt") == base64(sha256("saltpassword")).</p>
 */
class HashUtilTest {

    private static final String FOX = "The quick brown fox jumps over the lazy dog";
    private static final String FOX_HMAC_HEX =
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";

    // ------------------------------------------------------------------
    // HMAC-SHA256: known answer for both the hex-string and byte[] APIs.
    // ------------------------------------------------------------------

    @Test
    void hmacSha256MatchesKnownAnswerAsHex() {
        assertEquals(FOX_HMAC_HEX, HashUtil.toSha256HmacString(FOX, "key"));
    }

    @Test
    void hmacSha256ByteApiAgreesWithHexStringApi() {
        byte[] raw = HashUtil.getHmacSHA256(FOX, "key");
        assertEquals(FOX_HMAC_HEX, DataAdapter.bytesToHex(raw));
    }

    // ------------------------------------------------------------------
    // Exception path: an empty key is rejected by SecretKeySpec with
    // IllegalArgumentException before the Mac is even initialised — the
    // internal InvalidKeyException catch is unreachable through this API.
    // ------------------------------------------------------------------

    @Test
    void emptyHmacKeyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HashUtil.toSha256HmacString("data", ""));
    }

    // ------------------------------------------------------------------
    // Salting: deterministic under a fixed salt, randomized under random
    // salts, and always a base64-encoded 32-byte SHA-256 digest.
    // ------------------------------------------------------------------

    @Test
    void hashWithSaltMatchesKnownAnswer() {
        assertEquals("E2Ab2k6njlWge5iGbSvmvgdE44ZvE8AMgRyrYIoo8yI=",
                HashUtil.hashWithSalt("password", "salt"));
    }

    @Test
    void hashWithSaltIsDeterministic() {
        assertEquals(HashUtil.hashWithSalt("payload", "s1"), HashUtil.hashWithSalt("payload", "s1"));
    }

    @Test
    void differentSaltsYieldDifferentHashes() {
        assertNotEquals(HashUtil.hashWithSalt("payload", "s1"), HashUtil.hashWithSalt("payload", "s2"));
    }

    @Test
    void randomSaltIsBase64OfRequestedByteLength() {
        String salt = HashUtil.getRandomSalt(16);
        assertEquals(16, Base64.getDecoder().decode(salt).length);
        assertNotEquals(salt, HashUtil.getRandomSalt(16));
    }

    @Test
    void hashWithRandomSaltYieldsDistinct32ByteDigests() {
        String first = HashUtil.hashWithRandomSalt("payload");
        String second = HashUtil.hashWithRandomSalt("payload");
        assertEquals(32, Base64.getDecoder().decode(first).length);
        assertNotEquals(first, second); // random salt per call
    }

    // ------------------------------------------------------------------
    // Digest accessors: known SHA-256/MD5 answers plus algorithm identity.
    // ------------------------------------------------------------------

    @Test
    void sha256DigestMatchesKnownAnswer() {
        byte[] digest = HashUtil.getSHA256Digest().digest("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DataAdapter.bytesToHex(digest));
    }

    @Test
    void md5DigestMatchesKnownAnswer() {
        byte[] digest = HashUtil.getMD5Digest().digest("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("900150983cd24fb0d6963f7d28e17f72", DataAdapter.bytesToHex(digest));
    }

    @Test
    void hmacAccessorExposesHmacSha256() {
        assertNotNull(HashUtil.getSHA256Hmac());
        assertEquals("HmacSHA256", HashUtil.getSHA256Hmac().getAlgorithm());
    }

    // ------------------------------------------------------------------
    // next62_6: short Base62 codes masked to 24 bits — charset-shaped and
    // collision-free across a rapid burst (sequence counter separates calls
    // inside one millisecond, timestamp bits separate across milliseconds).
    // ------------------------------------------------------------------

    @Test
    void next62ProducesBase62Codes() {
        for (int i = 0; i < 50; i++) {
            assertTrue(HashUtil.next62_6().matches("[0-9A-Za-z]{1,6}"));
        }
    }

    @Test
    void next62BurstIsCollisionFree() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(HashUtil.next62_6());
        }
        assertEquals(500, seen.size());
    }
}
