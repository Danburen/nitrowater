package cn.nitrowater.core.services.auth.impl;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.api.AuthCode;
import cn.nitrowater.core.api.TokenPair;
import cn.nitrowater.core.common.TokenResult;
import cn.nitrowater.core.common.exceptions.AuthException;
import cn.nitrowater.core.utils.StringUtil;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.resp.auth.LoginClientData;
import cn.nitrowater.core.entity.user.User;
import cn.nitrowater.core.exception.DeviceInfoIncompleteException;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.core.infrastructure.security.RefreshTokenPayload;
import cn.nitrowater.core.infrastructure.utils.CookieUtil;
import cn.nitrowater.core.infrastructure.utils.ResponseUtil;
import cn.nitrowater.core.services.auth.AuthCoreService;
import cn.nitrowater.core.services.auth.code.CodeSenderFactory;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class AuthCoreServiceImpl implements AuthCoreService {
    private final AccessTokenServiceImpl authTokenServiceImpl;
    private final DeviceServiceImpl deviceService;
    private final UserRepository userRepository;
    private final CodeSenderFactory codeSenderFactory;

    @Override
    public LoginClientData buildLoginResponse(HttpServletResponse response, User user, String dfp, Boolean isNewUser) {
        TokenPair tokenPair = createNewTokens(user.getUid(), dfp);
        CookieUtil.setTokenCookie(response,tokenPair);
        ResponseUtil.setNoCacheSecurityHeaders(response);
        return new LoginClientData(tokenPair.accessToken(), tokenPair.accessExp(), isNewUser);
    }

    @Override
    public LoginClientData buildLoginResponse(HttpServletResponse response, User user, DeviceInfo info, Boolean isNewUser) {
        return Optional.ofNullable(info).map(
                i -> {
                    if(StringUtil.isBlank(i.getDeviceFp())){
                        throw new DeviceInfoIncompleteException();
                    }
                    TokenPair tokenPair = createNewTokens(user.getUid(), info.getDeviceFp());
                    CookieUtil.setTokenCookie(response,tokenPair);
                    ResponseUtil.setNoCacheSecurityHeaders(response);
                    return new LoginClientData(tokenPair.accessToken(), tokenPair.accessExp(), isNewUser);
                }
        ).orElseThrow(DeviceInfoIncompleteException::new);
    }

    @Override
    public TokenPair createNewTokens(long userUid, String deviceFingerprint) {
        String deviceId = deviceService.generateAndStoreDeviceId(userUid, deviceFingerprint);
        TokenResult accessToken = authTokenServiceImpl.genCacheNewAccTokenRevokeOlds(userUid, deviceId);
        TokenResult refreshToken = authTokenServiceImpl.genAndCacheRefToken(userUid, deviceId);
        return new TokenPair(
                accessToken.value(), accessToken.expiresIn(),
                refreshToken.value(), refreshToken.expiresIn());
    }

    /**
     * Return the refresh token
     *
     * @param refreshToken refresh value
     * @return Token result that contains value and expirations.
     */
    @Override
    public TokenPair refreshAccessToken(String refreshToken, String dfp) {
        StringUtil.isBlankThen(refreshToken, () -> {
            throw new AuthException(AuthCode.REAUTHORIZATION_REQUIRED);
        });// Missing refresh token
        // Resolve userUid from refresh token reverse index (not from access token)
        long userUid = authTokenServiceImpl.resolveUserUidByRefreshToken(refreshToken);
        RefreshTokenPayload payload = authTokenServiceImpl.validateRefreshToken(userUid, refreshToken, dfp);
        TokenResult RT = userRepository.findById(userUid).map(_ ->
                        authTokenServiceImpl.genAndCacheRefToken(userUid, payload.deviceId()))
                .orElseThrow(AuthException::new);
        TokenResult AT = authTokenServiceImpl.genCacheNewAccTokenRevokeOlds(userUid, payload.deviceId());
        return TokenPair.of(AT, RT);
    }
}
