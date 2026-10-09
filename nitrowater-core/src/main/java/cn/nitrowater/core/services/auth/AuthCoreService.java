package cn.nitrowater.core.services.auth;

import jakarta.servlet.http.HttpServletResponse;
import cn.nitrowater.lib.api.TokenPair;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.resp.auth.LoginClientData;
import cn.nitrowater.core.entity.user.User;

/**
 * Builds login responses and mints Phase-1 access/refresh token (AT/RT) pairs.
 *
 * @deprecated Phase-1 login/token shim, superseded by the OIDC provider (Spring Authorization
 *             Server) login flow. Retained only for the legacy {@code /api/auth/**} layer.
 */
@Deprecated
public interface AuthCoreService {
    LoginClientData buildLoginResponse(HttpServletResponse response, User user, String dfp, Boolean isNewUser);

    LoginClientData buildLoginResponse(HttpServletResponse response, User user, DeviceInfo info, Boolean isNewUser);

    TokenPair createNewTokens(long userUid, String deviceFingerprint);
    TokenPair refreshAccessToken(String refreshToken, String dfp);
}
