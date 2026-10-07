package cn.nitrowater.core.services.user;

import org.jetbrains.annotations.Nullable;
import cn.nitrowater.core.api.resp.AccountResp;
import cn.nitrowater.core.entity.user.UserDatum;

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
     * Save new phone to repo segment verified
     *
     * @param userUid  target user id
     * @param phone    new phone
     * @param verified
     * @return UserDatum
     */
    UserDatum saveNewPhone(long userUid, String phone, boolean verified);
    String getRawPhone(long userUid);
    @Nullable String getRawEmail(long userUid);

    /**
     * Get account info
     * @param userUid user id
     * @return account info
     */
    AccountResp getAccountInfo(long userUid);
}
