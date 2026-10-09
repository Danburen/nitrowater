package cn.nitrowater.lib.utils.generator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Generates random alphanumeric codes (invite codes, verification codes, ...).
 * Zero-dependency utility; caller supplies any uniqueness check (e.g. DB lookup).
 */
public final class RandomCodeGenerator {

    /** Uppercase letters + digits, excluding confusables (0, O, 1, l, I). */
    public static final String DEFAULT_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS_PER_CODE = 20;

    private RandomCodeGenerator() {
    }

    /**
     * Generate one random code with the default alphabet.
     *
     * @param length code length
     * @return random code
     */
    public static String generate(int length) {
        return generate(length, DEFAULT_ALPHABET);
    }

    /**
     * Generate one random code from the given alphabet.
     *
     * @param length   code length
     * @param alphabet character pool
     * @return random code
     */
    public static String generate(int length, String alphabet) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive: " + length);
        }
        if (alphabet == null || alphabet.isEmpty()) {
            throw new IllegalArgumentException("alphabet must not be empty");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Generate distinct random codes with the default alphabet, checking
     * uniqueness against {@code exists} (e.g. a DB query) in addition to
     * in-batch deduplication.
     *
     * @param count  number of codes to generate
     * @param length code length
     * @param exists null-safe existence check; codes failing it are skipped
     * @return distinct codes
     * @throws IllegalStateException if uniqueness cannot be satisfied
     */
    public static List<String> generateUnique(int count, int length, Predicate<String> exists) {
        return generateUnique(count, length, DEFAULT_ALPHABET, exists);
    }

    /**
     * Generate distinct random codes from the given alphabet, checking
     * uniqueness against {@code exists} in addition to in-batch deduplication.
     *
     * @param count    number of codes to generate
     * @param length   code length
     * @param alphabet character pool
     * @param exists   null-safe existence check; codes failing it are skipped
     * @return distinct codes
     * @throws IllegalStateException if uniqueness cannot be satisfied
     */
    public static List<String> generateUnique(int count, int length, String alphabet, Predicate<String> exists) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        List<String> codes = new ArrayList<>(count);
        Set<String> seen = new HashSet<>(count);
        int maxTries = count * MAX_ATTEMPTS_PER_CODE;
        int tries = 0;
        while (codes.size() < count) {
            if (++tries > maxTries) {
                throw new IllegalStateException(
                        "Failed to generate " + count + " unique codes after " + maxTries + " tries");
            }
            String code = generate(length, alphabet);
            if (!seen.add(code)) {
                continue;
            }
            if (exists != null && exists.test(code)) {
                continue;
            }
            codes.add(code);
        }
        return codes;
    }
}
