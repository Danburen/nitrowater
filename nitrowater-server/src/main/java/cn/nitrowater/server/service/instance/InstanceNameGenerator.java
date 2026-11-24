package cn.nitrowater.server.service.instance;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates a stable, human-friendly display name for a newly bound VeloChatX instance.
 *
 * <p>The name is {@code instance-} plus the first 6 base62 digits of {@code SHA-256(instanceId)}.
 * Deriving it from the UUID makes first binding retry-idempotent (the same machine always gets the
 * same base name), and the caller adds a salt when that name is already taken by the same owner.
 * Only the generator knows the alphabet and hash, so the wire format stays consistent.</p>
 */
@Component
public class InstanceNameGenerator {

    /** Base62 alphabet (digits, lowercase, uppercase) — URL-safe and case-sensitive. */
    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final int SUFFIX_LENGTH = 6;
    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length);

    /**
     * @param instanceId V-generated UUID
     * @return the base display name (e.g. {@code instance-1Im3eA})
     */
    public String generate(String instanceId) {
        return generate(instanceId, 0);
    }

    /**
     * @param instanceId V-generated UUID
     * @param salt       collision counter; {@code 0} yields the base name
     * @return the display name for this salt
     */
    public String generate(String instanceId, int salt) {
        String seed = salt == 0 ? instanceId : instanceId + "#" + salt;
        BigInteger hash = new BigInteger(1, sha256(seed));
        return "instance-" + base62(hash, SUFFIX_LENGTH);
    }

    /** @return the first {@code length} base62 digits of {@code value}, zero-padded */
    private static String base62(BigInteger value, int length) {
        StringBuilder sb = new StringBuilder(length);
        BigInteger current = value;
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[current.mod(BASE).intValue()]);
            current = current.divide(BASE);
        }
        return sb.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
