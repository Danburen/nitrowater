package cn.nitrowater.core.services.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.api.AuthCode;
import cn.nitrowater.lib.common.cache.RedisKeyBuilder;
import cn.nitrowater.lib.common.exceptions.AuthException;
import cn.nitrowater.lib.utils.DateUtil;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.lib.utils.codec.HashUtil;
import cn.nitrowater.core.api.auth.LoginResult;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.req.auth.PwdLoginReq;
import cn.nitrowater.core.api.req.auth.VerifyCodeDto;
import cn.nitrowater.core.entity.EncryptionDataKey;
import cn.nitrowater.core.entity.user.User;
import cn.nitrowater.core.entity.user.UserDatum;
import cn.nitrowater.core.exception.notfound.NotFoundException;
import cn.nitrowater.core.exception.threshold.AttemptLimitExceededException;
import cn.nitrowater.core.exception.threshold.DailyLimitExceededException;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.security.EncryptedKeyService;
import cn.nitrowater.core.infrastructure.security.RefreshTokenPayload;
import cn.nitrowater.core.infrastructure.utils.context.UserCtxHolder;
import cn.nitrowater.core.services.auth.LoginService;
import cn.nitrowater.core.services.auth.RegisterService;
import cn.nitrowater.core.services.auth.code.VerificationService;
import cn.nitrowater.core.services.user.UserCoreService;

import java.time.Duration;
import java.util.function.Supplier;

import static cn.nitrowater.lib.common.RedisKeyPrefix.THRESHOLD;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
// This service is shared by the Phase-2 OIDC login and the Phase-1 /api/auth/** shim;
// the latter still revokes the deprecated AT/RT on logout.
@SuppressWarnings("deprecation")
public class LoginServiceImpl implements LoginService {
    private final AccessTokenServiceImpl authTokenServiceImpl;
    private final UserDatumRepo userDatumRepo;
    private final EncryptedKeyService encryptedKeyService;
    private final CaptchaServiceImpl captchaService;
    private final VerificationService verificationService;
    private final RegisterService registerService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final RedisHelperHolder redisHelper;
    private final UserCoreService userCoreService;

    private static final int ADMIN_LOGIN_MAX_ATTEMPTS = 5;
    private static final long ADMIN_LOGIN_LOCK_MINUTES = 120;
    private static final int NORMAL_LOGIN_MAX_ATTEMPTS = 5;
    private static final long NORMAL_LOGIN_LOCK_MINUTES = 15;
    private static final long DAILY_NORMAL_LOGIN_MAX_ATTEMPTS = 30;
    private static final long DAILY_ADMIN_LOGIN_MAX_ATTEMPTS = 15;

    /** Short-term lockout window key: {@code threshold:fail:login:temp:{username}} */
    private static String loginTempFailKey(String username) {
        return RedisKeyBuilder.build(THRESHOLD, "fail", "login", "temp", username);
    }

    /** Daily limit window key: {@code threshold:fail:login:daily:{username}} */
    private static String loginDailyFailKey(String username) {
        return RedisKeyBuilder.build(THRESHOLD, "fail", "login", "daily", username);
    }

    @Override
    public void logout(String refreshToken, String dfp) {
        if(StringUtil.isBlank(refreshToken)) throw new AuthException(AuthCode.REAUTHORIZATION_REQUIRED);
        long userUid = UserCtxHolder.getUserUid();
        RefreshTokenPayload payload = authTokenServiceImpl.validateRefreshToken(userUid, refreshToken, dfp);
        authTokenServiceImpl.removeAccessToken(payload.userUid(), payload.deviceId());
        authTokenServiceImpl.removeRefreshToken(userUid, dfp, refreshToken);
    }

    @Override
    public LoginResult login(VerifyCodeDto dto, String codeKey) {
        DeviceInfo deviceInfo = dto.getDeviceInfo();
        if(dto.getScene() != VerifyScene.LOGIN){
            throw new AuthException(AuthCode.INVALID_VERIFY_SCENE);
        }
        VerifyChannel channel = dto.getChannel();

        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();
        UserDatum userDatum = null;
        if(channel == VerifyChannel.SMS){
            userDatum = userDatumRepo.findByPhoneHash(HashUtil.toSha256HmacString(dto.getTarget(), hmacKey.getEncryptedKey()))
                    .orElse(null);
        }else if(channel == VerifyChannel.EMAIL){
            userDatum = userDatumRepo.findByEmailHash(HashUtil.toSha256HmacString(dto.getTarget(), hmacKey.getEncryptedKey()))
                    .orElse(null);
        }

        if (userDatum == null) {
            User autoUser = registerService.autoRegister(
                    dto.getTarget(), channel, dto.getScene(), codeKey, dto.getCode(),
                    deviceInfo != null ? deviceInfo.getDeviceFp() : null
            );
            return new LoginResult(autoUser, true);
        }
        User u = userDatum.getUser();
        verificationService.verifyCode(dto.getTarget(),dto.getScene(),channel,codeKey,dto.getCode());
        // TODO(SSO): role-based admin distinction cut — user_role tables not migrated yet
        return recordAndBuildLoginResult(u, deviceInfo);
    }

    @Override
    public LoginResult adminLogin(PwdLoginReq body, String verifyUuidKey) {
        DeviceInfo deviceInfo = body.getDeviceInfo();
        // TODO(SSO): role-based admin verification cut — user_role tables not migrated yet
        return tryLogin(
                body.getIdentifier(),
                true,
                () -> verifyCredentials(body, verifyUuidKey),
                deviceInfo
        );
    }

    @Override
    public LoginResult login(PwdLoginReq body, String verifyUuidKey) {
        DeviceInfo deviceInfo = body.getDeviceInfo();
        return tryLogin(body.getIdentifier(), false,
                () -> verifyCredentials(body, verifyUuidKey), deviceInfo);
    }

    private LoginResult tryLogin(
            String username, boolean isAdmin,
            Supplier<User> credentialChecker,
            DeviceInfo deviceInfo
    ) {
        String shortFailKey = loginTempFailKey(username);
        String dailyFailKey = loginDailyFailKey(username);
        checkShortTermLockout(shortFailKey, isAdmin);
        checkDailyLimit(dailyFailKey, isAdmin);

        User u;
        try {
            u = credentialChecker.get();
        } catch (AuthException e) {
            if (AuthCode.USERNAME_OR_PASSWORD_INCORRECT.getCode().equals(e.getErrorCode())) {
                incrementFailCounters(shortFailKey, dailyFailKey, isAdmin);
            }
            throw e;
        }

        // TODO(SSO): role match check (isAdmin != isUserAdmin) cut — user_role tables not migrated yet
        redisHelper.del(shortFailKey);
        return recordAndBuildLoginResult(u, deviceInfo);
    }

    private LoginResult recordAndBuildLoginResult(User u, DeviceInfo deviceInfo) {
        // TODO(SSO): stats recording / login ban check / online last-active update cut — business services not migrated
        return new LoginResult(u, false);
    }

    private void checkShortTermLockout(String failKey, boolean isAdmin) {
        String val = redisHelper.getValue(failKey);
        int threshold = isAdmin ? ADMIN_LOGIN_MAX_ATTEMPTS : NORMAL_LOGIN_MAX_ATTEMPTS;
        if (val != null && Integer.parseInt(val) >= threshold) {
            long lockMinutes = isAdmin ? ADMIN_LOGIN_LOCK_MINUTES : NORMAL_LOGIN_LOCK_MINUTES;
            throw new AttemptLimitExceededException(lockMinutes);
        }
    }

    private void checkDailyLimit(String failKey, boolean isAdmin) {
        String val = redisHelper.getValue(failKey);
        long threshold = isAdmin ? DAILY_ADMIN_LOGIN_MAX_ATTEMPTS : DAILY_NORMAL_LOGIN_MAX_ATTEMPTS;
        if (val != null && Long.parseLong(val) >= threshold) {
            log.warn("Daily login limit exceeded: username key={}", failKey);
            throw new DailyLimitExceededException();
        }
    }

    private void incrementFailCounters(String shortFailKey, String dailyFailKey, boolean isAdmin) {
        long lockMinutes = isAdmin ? ADMIN_LOGIN_LOCK_MINUTES : NORMAL_LOGIN_LOCK_MINUTES;
        redisHelper.increment(shortFailKey, Duration.ofMinutes(lockMinutes));
        redisHelper.increment(dailyFailKey, Duration.ofSeconds(DateUtil.getSecondsUntilMidnight()));
    }

    private User verifyCredentials(PwdLoginReq body, String verifyUuidKey) {
        // Resolve identifier (phone/email/username) → userUid → User
        Long userUid;
        try {
            userUid = userCoreService.resolveUid(body.getIdentifier());
        } catch (NotFoundException e) {
            throw new AuthException(AuthCode.USERNAME_OR_PASSWORD_INCORRECT);
        }
        User uu = userCoreService.getUser(userUid);
        if (!captchaService.verifyCode(verifyUuidKey, body.getCaptcha()))
            throw new AuthException(AuthCode.CAPTCHA_INVALID);
        if (!encoder.matches(body.getPassword(), uu.getPasswordHash()))
            throw new AuthException(AuthCode.USERNAME_OR_PASSWORD_INCORRECT);
        return uu;
    }
}
