package cn.nitrowater.core.application.auth;

import cn.nitrowater.core.api.auth.LoginResult;
import cn.nitrowater.core.api.req.auth.PwdLoginReq;

public interface LoginService {
    /**
     * Normal pwd login, must not be an administrator
     * @param body {@link PwdLoginReq} (contains deviceFp)
     * @param verifyUuidKey captcha verify login key
     * @return {@link LoginResult}
     */
    LoginResult login(PwdLoginReq body, String verifyUuidKey);
}
