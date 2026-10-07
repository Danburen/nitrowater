package cn.nitrowater.core.lib.services.auth;

import jakarta.servlet.http.HttpServletResponse;
import cn.nitrowater.core.lib.api.TokenPair;
import cn.nitrowater.core.lib.api.req.auth.DeviceInfo;
import cn.nitrowater.core.lib.api.resp.auth.LoginClientData;
import cn.nitrowater.core.lib.entity.user.User;

public interface AuthCoreService {
    LoginClientData buildLoginResponse(HttpServletResponse response, User user, String dfp, Boolean isNewUser);

    LoginClientData buildLoginResponse(HttpServletResponse response, User user, DeviceInfo info, Boolean isNewUser);

    TokenPair createNewTokens(long userUid, String deviceFingerprint);
    TokenPair refreshAccessToken(String refreshToken, String dfp);
}
