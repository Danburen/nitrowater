package cn.nitrowater.core.services.account;

import jakarta.annotation.Nullable;
import cn.nitrowater.core.services.auth.code.VerificationService;
import cn.nitrowater.lib.common.TokenResult;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.resp.auth.CodeResult;

/**
 * Core account management service.
 * <p>Handles password/email/phone operations, distinct from login/auth semantics.</p>
 */
public interface AccountCoreService {

    /**
     * Update password (authenticated via reAuthToken).
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void changePwd(long userUid, String newPwd, String confirmPwd, @Nullable DeviceInfo deviceInfo);

    /**
     * Set password for users who don't have one yet (authenticated via reAuthToken).
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void setPassword(long userUid, String newPwd, String confirmPwd, @Nullable DeviceInfo deviceInfo);

    /**
     * Activate email binding (authenticated via reAuthToken).
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void activateEmail(long userUid, String email, @Nullable DeviceInfo deviceInfo);

    /**
     * Change email — saves as unverified, sends activation code to the new email.
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    CodeResult changeEmail(long userUid, String newEmail, @Nullable DeviceInfo deviceInfo);

    /**
     * Verify the activation code for a pending email change and mark the email as verified.
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void verifyChangeEmail(String verifyKey, String code, long userUid, @Nullable DeviceInfo deviceInfo);

    /**
     * Clean up unverified email records whose expiry has passed.
     */
    void cleanUnverifiedEmail();

    /**
     * Change phone number (authenticated via reAuthToken).
     * Saves new phone as unverified and sends activate code.
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    CodeResult changePhone(long userUid, String newPhone, @Nullable DeviceInfo deviceInfo);

    /**
     * Verify the activation code for a pending phone change and mark the phone as verified.
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void verifyChangePhone(String verifyKey, String code, long userUid, @Nullable DeviceInfo deviceInfo);

    /**
     * Activate phone number (authenticated via reAuthToken).
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void activatePhone(long userUid, String phone, @Nullable DeviceInfo deviceInfo);

    /**
     * Unbind email (authenticated via reAuthToken).
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void unbindEmail(long userUid, String email, @Nullable DeviceInfo deviceInfo);

    /**
     * Reset password using a reAuthToken (consumed one-time).
     * Called after the forgot-password SMS verification flow.
     *
     * @param deviceInfo device fingerprint for audit logging, may be {@code null}
     */
    void resetPasswordByToken(Long userUid, String newPwd, String confirmPwd, @Nullable DeviceInfo deviceInfo);

    /**
     * Step 1: Initiate forgot-password re-authentication.
     * <p>Resolves identifier → uid → bound phone, sends SMS code,
     * stores the phone in Redis key {@code op:verify:context:fp:{verifyKey}} for later verification.</p>
     *
     * @param identifier phone/email/username
     * @return reAuthKey (the SMS verify key), or {@code null} if no bound phone found
     */
    @Nullable
    String initiateForgotPasswordReAuth(String identifier);

    /**
     * Step 2: Verify forgot-password SMS code and generate a one-time token.
     * <p>Reads the phone from Redis key {@code op:verify:context:fp:{reAuthKey}} (not deleted),
     * {@link VerificationService#verifyCode verifies}
     * the code, then deletes the Redis key only on success — allowing retry on wrong code.</p>
     *
     * @param reAuthKey the verify key returned from step 1
     * @param code      the SMS code from the user
     * @return {@link TokenResult} containing the one-time token, or {@code null} if expired
     */
    @Nullable
    TokenResult verifyForgotPasswordReAuth(String reAuthKey, String code);
}
