package cn.nitrowater.core.application.auth;

import cn.nitrowater.core.api.auth.LoginResult;
import cn.nitrowater.core.api.req.auth.PwdLoginReq;
import cn.nitrowater.core.api.req.auth.VerifyCodeDto;

public interface LoginService {
    /**
     * Normal pwd login, must not be an administrator
     * @param body {@link PwdLoginReq} (contains deviceFp)
     * @param verifyUuidKey captcha verify login key
     * @return {@link LoginResult}
     */
    LoginResult login(PwdLoginReq body, String verifyUuidKey);

    /**
     * One-time-code login (SMS / email).
     *
     * <p>Only the code's own channel is consulted: a phone code can never resolve a username or
     * email, and vice versa. System/test accounts are rejected because they are password-only.</p>
     *
     * @param body         target, channel, code and scene (must be {@code LOGIN})
     * @param verifyUuidKey cookie key of the code that was sent
     * @return {@link LoginResult}
     */
    LoginResult loginByCode(VerifyCodeDto body, String verifyUuidKey);
}
