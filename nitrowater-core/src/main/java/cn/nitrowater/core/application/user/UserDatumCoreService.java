package cn.nitrowater.core.application.user;

import org.jetbrains.annotations.Nullable;
import cn.nitrowater.core.api.resp.AccountResp;
import cn.nitrowater.core.domain.user.UserDatum;

public interface UserDatumCoreService {
    UserDatum getUserDatum(long userUid);

    /**
     * Save new email to repo
     *
     * @param userUid  target user id
     * @param email    new email
     * @param verified
     */
    UserDatum saveNewEmail(long userUid, String email, boolean verified);

    /**
     * Checks that an email can be bound to this account without changing the current binding.
     *
     * @param userUid owner uid
     * @param email candidate email
     */
    void validateEmailAvailable(long userUid, String email);

    /**
     * Marks the current email as verified.
     *
     * @param userUid owner uid
     */
    void markEmailVerified(long userUid);

    /**
     * Save new phone to repo segment verified
     *
     * @param userUid  target user id
     * @param phone    new phone
     * @param verified
     * @return UserDatum
     */
    UserDatum saveNewPhone(long userUid, String phone, boolean verified);

    /**
     * Checks that a phone can be bound to this account without changing the current binding.
     *
     * @param userUid owner uid
     * @param phone candidate phone
     */
    void validatePhoneAvailable(long userUid, String phone);

    /**
     * Marks the current phone as verified.
     *
     * @param userUid owner uid
     */
    void markPhoneVerified(long userUid);
    String getRawPhone(long userUid);
    @Nullable String getRawEmail(long userUid);

    /**
     * Get account info
     * @param userUid user id
     * @return account info
     */
    AccountResp getAccountInfo(long userUid);
}
