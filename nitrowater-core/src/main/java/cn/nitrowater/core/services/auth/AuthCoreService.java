package cn.nitrowater.core.services.auth;

import jakarta.servlet.http.HttpServletResponse;
import cn.nitrowater.core.api.TokenPair;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.resp.auth.LoginClientData;
import cn.nitrowater.core.entity.user.User;

public interface AuthCoreService {
    LoginClientData buildLoginResponse(HttpServletResponse response, User user, String dfp, Boolean isNewUser);

    LoginClientData buildLoginResponse(HttpServletResponse response, User user, DeviceInfo info, Boolean isNewUser);

    TokenPair createNewTokens(long userUid, String deviceFingerprint);
    TokenPair refreshAccessToken(String refreshToken, String dfp);
}
