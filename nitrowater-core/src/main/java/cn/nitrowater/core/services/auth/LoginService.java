package cn.nitrowater.core.services.auth;

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
     * Admin pwd login the user must have thr role of "ADMIN"
     * @param body pwd login body (contains deviceFp)
     * @param verifyUuidKey verify uuid key
     * @return {@link LoginResult}
     */
    LoginResult adminLogin(PwdLoginReq body, String verifyUuidKey);

    void logout(String refreshToken, String dfp);

    /***
     * Login by code, user must not be admin
     * @param dto verify code dto (contains deviceFp)
     * @param codeKey code key
     * @return  login result containing user and whether it was auto-registered
     */
    LoginResult login(VerifyCodeDto dto, String codeKey);

}
