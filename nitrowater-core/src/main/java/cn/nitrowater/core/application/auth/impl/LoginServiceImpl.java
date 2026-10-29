package cn.nitrowater.core.application.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.api.AuthCode;
import cn.nitrowater.lib.common.cache.RedisKeyBuilder;
import cn.nitrowater.lib.common.exceptions.AuthException;
import cn.nitrowater.lib.utils.DateUtil;
import cn.nitrowater.core.api.auth.LoginResult;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.req.auth.PwdLoginReq;
import cn.nitrowater.core.api.req.auth.VerifyCodeDto;
import cn.nitrowater.core.application.auth.AuthIdentifierUtil;
import cn.nitrowater.core.application.auth.code.VerificationService;
import cn.nitrowater.core.domain.user.AccountStatus;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.exception.notfound.NotFoundException;
import cn.nitrowater.core.exception.threshold.AttemptLimitExceededException;
import cn.nitrowater.core.exception.threshold.DailyLimitExceededException;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.application.auth.LoginService;
import cn.nitrowater.core.application.user.UserCoreService;

import java.time.Duration;
import java.util.Locale;

import static cn.nitrowater.lib.common.RedisKeyPrefix.THRESHOLD;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {
    private final CaptchaServiceImpl captchaService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final RedisHelperHolder redisHelper;
    private final UserCoreService userCoreService;
    private final VerificationService verificationService;

    private static final int LOGIN_MAX_ATTEMPTS = 5;
    private static final long LOGIN_LOCK_MINUTES = 15;
    private static final long DAILY_LOGIN_MAX_ATTEMPTS = 30;

    /** Bcrypt hash of a throwaway string: burns the same CPU a real account would when the
     * identifier does not exist, so response time does not reveal existence. */
    private static final String DUMMY_PASSWORD_HASH = new BCryptPasswordEncoder().encode("dummy-password");

    /** Short-term lockout window key: {@code threshold:fail:login:temp:{username}} */
    private static String loginTempFailKey(String username) {
        return RedisKeyBuilder.build(THRESHOLD, "fail", "login", "temp", username);
    }

    /** Daily limit window key: {@code threshold:fail:login:daily:{username}} */
    private static String loginDailyFailKey(String username) {
        return RedisKeyBuilder.build(THRESHOLD, "fail", "login", "daily", username);
    }

    /**
     * Canonical lockout key for a login identifier, so case/whitespace variants of the same
     * email, phone or username cannot each get their own fresh attempt budget.
     */
    private static String loginIdentifierKey(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (AuthIdentifierUtil.isEmail(value)) {
            return AuthIdentifierUtil.normalizeEmail(value);
        }
        if (AuthIdentifierUtil.isPhone(value)) {
            return AuthIdentifierUtil.normalizePhone(value);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public LoginResult login(PwdLoginReq body, String verifyUuidKey) {
        String identifier = loginIdentifierKey(body.getIdentifier());
        String shortFailKey = loginTempFailKey(identifier);
        String dailyFailKey = loginDailyFailKey(identifier);
        checkShortTermLockout(shortFailKey);
        checkDailyLimit(dailyFailKey);

        User u;
        try {
            u = verifyCredentials(body, verifyUuidKey);
        } catch (AuthException e) {
            if (AuthCode.USERNAME_OR_PASSWORD_INCORRECT.getCode().equals(e.getErrorCode())) {
                incrementFailCounters(shortFailKey, dailyFailKey);
            }
            throw e;
        }
        redisHelper.del(shortFailKey);
        // TODO(SSO): stats recording / login ban check / online last-active update cut — business
        // services not migrated yet.
        return new LoginResult(u);
    }

    @Override
    public LoginResult loginByCode(VerifyCodeDto body, String verifyUuidKey) {
        if (body.getScene() != VerifyScene.LOGIN) {
            throw new AuthException(AuthCode.INVALID_VERIFY_SCENE);
        }
        VerifyChannel channel = body.getChannel();
        String normalized = channel == VerifyChannel.EMAIL
                ? AuthIdentifierUtil.normalizeEmail(body.getTarget())
                : AuthIdentifierUtil.normalizePhone(body.getTarget());

        // Prove control of the target FIRST. A code is only ever sent for registered, code-login
        // eligible, active accounts, so this makes every rejected target look like a bad code
        // instead of an account-existence / status oracle.
        verificationService.verifyCode(normalized, VerifyScene.LOGIN, channel, verifyUuidKey, body.getCode());

        Long userUid;
        try {
            userUid = userCoreService.resolveUidByTarget(normalized, channel);
        } catch (NotFoundException e) {
            throw new AuthException(AuthCode.VERIFY_TARGET_UNSUPPORTED);
        }
        User u = userCoreService.getUser(userUid);
        if (!u.getUserType().isCodeLoginAllowed()) {
            // System/test accounts are password-only, even if a mailbox is attached to them.
            throw new AuthException(AuthCode.USERNAME_OR_PASSWORD_INCORRECT);
        }
        if (u.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AuthException(AuthCode.ACCOUNT_DISABLED);
        }
        // TODO(SSO): stats recording / login ban check / online last-active update cut — business
        // services not migrated yet.
        return new LoginResult(u);
    }

    private void checkShortTermLockout(String failKey) {
        String val = redisHelper.getValue(failKey);
        if (val != null && Integer.parseInt(val) >= LOGIN_MAX_ATTEMPTS) {
            throw new AttemptLimitExceededException(LOGIN_LOCK_MINUTES);
        }
    }

    private void checkDailyLimit(String failKey) {
        String val = redisHelper.getValue(failKey);
        if (val != null && Long.parseLong(val) >= DAILY_LOGIN_MAX_ATTEMPTS) {
            log.warn("Daily login limit exceeded: username key={}", failKey);
            throw new DailyLimitExceededException();
        }
    }

    private void incrementFailCounters(String shortFailKey, String dailyFailKey) {
        redisHelper.increment(shortFailKey, Duration.ofMinutes(LOGIN_LOCK_MINUTES));
        redisHelper.increment(dailyFailKey, Duration.ofSeconds(DateUtil.getSecondsUntilMidnight()));
    }

    private User verifyCredentials(PwdLoginReq body, String verifyUuidKey) {
        // Captcha first: checked last, the two failure codes became an existence oracle ("unknown
        // identifier" never reached the captcha, "known identifier" did).
        if (!captchaService.verifyCode(verifyUuidKey, body.getCaptcha()))
            throw new AuthException(AuthCode.CAPTCHA_INVALID);

        Long userUid;
        try {
            userUid = userCoreService.resolveUid(body.getIdentifier());
        } catch (NotFoundException e) {
            encoder.matches(body.getPassword(), DUMMY_PASSWORD_HASH);
            throw new AuthException(AuthCode.USERNAME_OR_PASSWORD_INCORRECT);
        }
        User uu = userCoreService.getUser(userUid);
        // Passwordless accounts (registered with only a phone/email code) must not turn a null
        // hash into an IllegalArgumentException -> 500; they simply cannot use password login.
        if (body.getPassword() == null
                || uu.getPasswordHash() == null
                || uu.getPasswordHash().isBlank()
                || !encoder.matches(body.getPassword(), uu.getPasswordHash()))
            throw new AuthException(AuthCode.USERNAME_OR_PASSWORD_INCORRECT);
        // Checked only after the password matches: a suspended/deactivated account must not become
        // an existence oracle for anyone who is merely guessing credentials.
        if (uu.getAccountStatus() != AccountStatus.ACTIVE)
            throw new AuthException(AuthCode.ACCOUNT_DISABLED);
        return uu;
    }
}
