package cn.nitrowater.lib.utils.codec;
import cn.nitrowater.lib.utils.DataAdapter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class HashUtil {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Base62 alphabet, shared with {@link Base62}. */
    private static final char[] DIGITS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final ThreadLocal<MessageDigest> MD5_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

    private static final ThreadLocal<MessageDigest> SHA256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

    private static final ThreadLocal<Mac> SHA256_HMAC = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

    public static String toSha256HmacString(String data, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
            SHA256_HMAC.get().init(secretKey);
            byte[] hmacBytes = SHA256_HMAC.get().doFinal(data.getBytes(StandardCharsets.UTF_8));
            return DataAdapter.bytesToHex(hmacBytes);
        }catch (InvalidKeyException e){
            throw new RuntimeException(e);
        }
    }

    public static byte[] getHmacSHA256(String data, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
            SHA256_HMAC.get().init(secretKey);
            return SHA256_HMAC.get().doFinal(data.getBytes(StandardCharsets.UTF_8));
        }catch (InvalidKeyException e){
            throw new RuntimeException(e);
        }
    }

    public static String getRandomSalt(int length) {
        byte[] saltBytes = new byte[length];
        SECURE_RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    public static String hashWithSalt(String original,String salt){
        byte[] hash = SHA256_DIGEST.get().digest(salt.concat(original).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    public static String hashWithRandomSalt(String original){
        byte[] hash = SHA256_DIGEST.get()
                .digest(getRandomSalt(32)
                        .concat(original)
                        .getBytes(StandardCharsets.UTF_8)
                );
        return Base64.getEncoder().encodeToString(hash);
    }

    public static MessageDigest getSHA256Digest() {
        return SHA256_DIGEST.get();
    }

    public static MessageDigest getMD5Digest() {
        return MD5_DIGEST.get();
    }

    /**
     * SHA256_HMAC Message Authentication Code generator
     * @return sha256 hmac
     */
    public static Mac getSHA256Hmac() {
        return SHA256_HMAC.get();
    }

    /**
     * A random 6-character base62 code (62^6 ≈ 5.7e10 values).
     *
     * <p>Used as the short suffix when a slug collides. It must not be derived from a clock:
     * the previous snowflake-masked version shared its last bits with the millisecond, so codes
     * generated in a burst could repeat — exactly when a slug collision is being retried.</p>
     *
     * @return exactly 6 base62 characters
     */
    public static String next62_6() {
        char[] out = new char[6];
        for (int i = 0; i < out.length; i++) {
            out[i] = DIGITS[SECURE_RANDOM.nextInt(DIGITS.length)];
        }
        return new String(out);
    }
}
