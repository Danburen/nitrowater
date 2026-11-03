package cn.nitrowater.core.application.account.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.common.TokenResult;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.event.UserSessionsInvalidatedEvent;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.domain.user.UserDatum;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.core.application.account.AccountCoreService;
import cn.nitrowater.core.application.auth.AuthIdentifierUtil;
import cn.nitrowater.core.application.auth.SingleUseTokenService;
import cn.nitrowater.core.application.auth.code.VerificationService;
import cn.nitrowater.core.application.user.UserCoreService;
import cn.nitrowater.core.application.user.UserDatumCoreService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import cn.nitrowater.lib.utils.StringUtil;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountCoreServiceImpl implements AccountCoreService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserDatumRepo userDatumRepo;
    private final UserCoreService userCoreService;
    private final UserDatumCoreService userDatumCoreService;
    private final VerificationService verificationService;
    private final RedisHelperHolder redisHelperHolder;
    private final SingleUseTokenService singleUseTokenService;
    private final ApplicationEventPublisher eventPublisher;

    private static final String FP_CTX_PREFIX = "op:verify:context:fp:";
    private static final Duration FP_CTX_TTL = Duration.ofMinutes(5);
    private static final String CONTACT_CTX_PREFIX = "op:verify:context:contact:";
    private static final Duration CONTACT_CTX_TTL = Duration.ofMinutes(30);

    private static String contactContextKey(VerifyChannel channel, long uid, String verifyKey) {
        return CONTACT_CTX_PREFIX + channel.name().toLowerCase(Locale.ROOT) + ":" + uid + ":" + verifyKey;
    }


    @Transactional
    @Override
    public void changePwd(long userUid, String newPwd, String confirmPwd, DeviceInfo deviceInfo) {
        applyNewPassword(userUid, newPwd, confirmPwd);
    }

    @Transactional
    @Override
    public void setPassword(long userUid, String newPwd, String confirmPwd, DeviceInfo deviceInfo) {
        if (!newPwd.equals(confirmPwd)) {
            throw new BizException(BaseResponseCode.PASSWORD_TWO_PASSWORD_NOT_EQUAL);
        }
        User user = userRepository.findById(userUid)
                .orElseThrow(() -> new BizException(BaseResponseCode.USER_NOT_FOUND));
        if (user.getPasswordHash() != null) {
            throw new BizException(BaseResponseCode.PASSWORD_ALREADY_SET);
        }
        user.setPasswordHash(encoder.encode(newPwd));
        userRepository.save(user);
        invalidateSessions(userUid);
    }

    // -- Email operations --

    @Override
    @Transactional
    public CodeResult activateEmail(long userUid, String email, DeviceInfo deviceInfo) {
        String current = userDatumCoreService.getRawEmail(userUid);
        if (current == null) {
            if (StringUtil.isBlank(email)) {
                throw new BizException(BaseResponseCode.EMAIL_NOT_FOUND);
            }
            return changeEmail(userUid, email, deviceInfo);
        }
        String requested = AuthIdentifierUtil.normalizeEmail(email);
        if (requested != null && !AuthIdentifierUtil.isEmail(requested)) {
            throw new BizException(BaseResponseCode.EMAIL_INVALID);
        }
        if (requested != null && !current.equals(requested)) {
            throw new BizException(BaseResponseCode.EMAIL_INVALID);
        }
        UserDatum datum = userDatumCoreService.getUserDatum(userUid);
        if (Boolean.TRUE.equals(datum.getEmailVerified())) {
            throw new BizException(BaseResponseCode.EMAIL_INVALID);
        }
        return sendContactCode(userUid, VerifyChannel.EMAIL, current, false);
    }

    @Override
    @Transactional
    public CodeResult changeEmail(long userUid, String newEmail, DeviceInfo deviceInfo) {
        String email = AuthIdentifierUtil.normalizeEmail(newEmail);
        if (!AuthIdentifierUtil.isEmail(email)) {
            throw new BizException(BaseResponseCode.EMAIL_INVALID);
        }
        userDatumCoreService.validateEmailAvailable(userUid, email);
        return sendContactCode(userUid, VerifyChannel.EMAIL, email, true);
    }

    @Override
    @Transactional
    public void verifyChangeEmail(String verifyKey, String code, long userUid, DeviceInfo deviceInfo) {
        String contextKey = contactContextKey(VerifyChannel.EMAIL, userUid, verifyKey);
        String context = redisHelperHolder.getValue(contextKey);
        if (context == null) {
            throw new BizException(BaseResponseCode.INVALID_TOKEN_OR_EXPIRED);
        }
        String[] parts = context.split("\\|", 2);
        if (parts.length != 2) {
            throw new BizException(BaseResponseCode.INVALID_TOKEN_OR_EXPIRED);
        }
        boolean isChange = "change".equals(parts[0]);
        String email = parts[1];
        verificationService.verifyCode(email, VerifyScene.ACTIVATE, VerifyChannel.EMAIL, verifyKey, code);
        if (isChange) {
            userDatumCoreService.saveNewEmail(userUid, email, true);
        } else {
            userDatumCoreService.markEmailVerified(userUid);
        }
        redisHelperHolder.del(contextKey);
    }

    @Override
    @Async
    @Transactional
    public void cleanUnverifiedEmail() {
        List<UserDatum> list = userDatumRepo.findUserDatumByEmailVerifiedFalse();
        list.forEach(ud -> {
            if (ud.getEmailExpireAt() != null && ud.getEmailExpireAt().isBefore(Instant.now())) {
                ud.setEmailEncrypted(null);
                ud.setEmailHash(null);
            }
        });
    }

    // -- Phone operations --

    @Override
    @Transactional
    public CodeResult changePhone(long userUid, String newPhone, DeviceInfo deviceInfo) {
        String phone = AuthIdentifierUtil.normalizePhone(newPhone);
        if (!AuthIdentifierUtil.isPhone(phone)) {
            throw new BizException(BaseResponseCode.PHONE_NUMBER_EMPTY_OR_INVALID);
        }
        userDatumCoreService.validatePhoneAvailable(userUid, phone);
        return sendContactCode(userUid, VerifyChannel.SMS, phone, true);
    }

    @Override
    @Transactional
    public void verifyChangePhone(String verifyKey, String code, long userUid, DeviceInfo deviceInfo) {
        String contextKey = contactContextKey(VerifyChannel.SMS, userUid, verifyKey);
        String context = redisHelperHolder.getValue(contextKey);
        if (context == null) {
            throw new BizException(BaseResponseCode.INVALID_TOKEN_OR_EXPIRED);
        }
        String[] parts = context.split("\\|", 2);
        if (parts.length != 2) {
            throw new BizException(BaseResponseCode.INVALID_TOKEN_OR_EXPIRED);
        }
        boolean isChange = "change".equals(parts[0]);
        String phone = parts[1];
        verificationService.verifyCode(phone, VerifyScene.ACTIVATE, VerifyChannel.SMS, verifyKey, code);
        if (isChange) {
            userDatumCoreService.saveNewPhone(userUid, phone, true);
        } else {
            userDatumCoreService.markPhoneVerified(userUid);
        }
        redisHelperHolder.del(contextKey);
    }

    @Override
    @Transactional
    public CodeResult activatePhone(long userUid, String phone, DeviceInfo deviceInfo) {
        String current = userDatumCoreService.getRawPhone(userUid);
        if (current == null) {
            if (StringUtil.isBlank(phone)) {
                throw new BizException(BaseResponseCode.NOT_FOUND);
            }
            return changePhone(userUid, phone, deviceInfo);
        }
        String requested = AuthIdentifierUtil.normalizePhone(phone);
        if (requested != null && !AuthIdentifierUtil.isPhone(requested)) {
            throw new BizException(BaseResponseCode.PHONE_NUMBER_EMPTY_OR_INVALID);
        }
        if (requested != null && !current.equals(requested)) {
            throw new BizException(BaseResponseCode.PHONE_NUMBER_ALREADY_USED);
        }
        UserDatum datum = userDatumCoreService.getUserDatum(userUid);
        if (Boolean.TRUE.equals(datum.getPhoneVerified())) {
            throw new BizException(BaseResponseCode.PHONE_NUMBER_ALREADY_USED);
        }
        return sendContactCode(userUid, VerifyChannel.SMS, current, false);
    }

    private CodeResult sendContactCode(long userUid, VerifyChannel channel, String target, boolean change) {
        CodeResult result = verificationService.sendCodeForAuthenticated(
                target, channel, VerifyScene.ACTIVATE);
        redisHelperHolder.set(contactContextKey(channel, userUid, result.getKey()),
                (change ? "change" : "activate") + "|" + target, CONTACT_CTX_TTL);
        return result;
    }

    // -- Unbind --

    @Override
    @Transactional
    public void unbindEmail(long userUid, String email, DeviceInfo deviceInfo) {
        String emailRaw = userDatumCoreService.getRawEmail(userUid);
        if (emailRaw == null) {
            throw new BizException(BaseResponseCode.EMAIL_NOT_FOUND);
        }
        String requested = AuthIdentifierUtil.normalizeEmail(email);
        if (requested != null && !emailRaw.equals(requested)) {
            throw new BizException(BaseResponseCode.EMAIL_INVALID);
        }
        UserDatum ud = userDatumCoreService.getUserDatum(userUid);
        ud.setEmailExpireAt(null);
        ud.setEmailEncrypted(null);
        ud.setEmailHash(null);
        ud.setEmailVerified(false);
        userDatumRepo.save(ud);
    }

    @Override
    @Transactional
    public void resetPasswordByToken(Long userUid, String newPwd, String confirmPwd, DeviceInfo deviceInfo) {
        applyNewPassword(userUid, newPwd, confirmPwd);
    }

    /** Both password-change entry points: confirm-check, persist, then kill the account's sessions. */
    private void applyNewPassword(long userUid, String newPwd, String confirmPwd) {
        if (!newPwd.equals(confirmPwd)) {
            throw new BizException(BaseResponseCode.PASSWORD_TWO_PASSWORD_NOT_EQUAL);
        }
        userCoreService.changePwd(userUid, newPwd);
        invalidateSessions(userUid);
    }

    /**
     * Kills every session of the account after a credential change: the OIDC authorizations (whose
     * stored principal still carries the old identity) and the legacy AT/RT in Redis. Without this a
     * stolen session survives a password reset for up to the refresh-token lifetime.
     */
    private void invalidateSessions(long userUid) {
        eventPublisher.publishEvent(new UserSessionsInvalidatedEvent(userUid,
                UserSessionsInvalidatedEvent.Reason.CREDENTIALS_CHANGED));
    }

    @Override
    @Nullable
    public String initiateForgotPasswordReAuth(String identifier) {
        try {
            Long uid = userCoreService.resolveUid(identifier);
            String phone = userDatumCoreService.getRawPhone(uid);
            String email = userDatumCoreService.getRawEmail(uid);
            VerifyChannel channel;
            String target;
            if (phone != null) {
                channel = VerifyChannel.SMS;
                target = phone;
            } else if (email != null) {
                channel = VerifyChannel.EMAIL;
                target = email;
            } else {
                return null;
            }
            CodeResult result = verificationService.sendCodeForAuthenticated(
                    target, channel, VerifyScene.FORGOT_PASSWORD);
            String reAuthKey = result.getKey();
            redisHelperHolder.set(FP_CTX_PREFIX + reAuthKey,
                    channel.name() + "|" + target, FP_CTX_TTL);
            return reAuthKey;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Nullable
    public TokenResult verifyForgotPasswordReAuth(String reAuthKey, String code) {
        String context = redisHelperHolder.getValue(FP_CTX_PREFIX + reAuthKey);
        if (context == null) return null;
        int separator = context.indexOf('|');
        if (separator <= 0 || separator == context.length() - 1) {
            return null;
        }
        VerifyChannel channel = VerifyChannel.valueOf(context.substring(0, separator));
        String target = context.substring(separator + 1);
        verificationService.verifyCode(target, VerifyScene.FORGOT_PASSWORD, channel,
                reAuthKey, code);
        // Code verified — now delete the key (only on success, allowing retry on wrong code)
        redisHelperHolder.del(FP_CTX_PREFIX + reAuthKey);
        Long uid = userCoreService.resolveUid(target);
        return singleUseTokenService.generateVerifyToken(VerifyScene.FORGOT_PASSWORD.name(), uid);
    }

    @Override
    @Transactional
    public void updateAvatarResourceUuid(long userUid, String resourceUuid) {
        String normalized = (StringUtil.isBlank(resourceUuid)) ? null : resourceUuid.trim();
        int updated = userRepository.updateAvatarResourceUuid(userUid, normalized);
        if (updated == 0) {
            throw new BizException(BaseResponseCode.USER_NOT_FOUND);
        }
    }
}
