package cn.nitrowater.core.services.account.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.common.TokenResult;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.entity.user.User;
import cn.nitrowater.core.entity.user.UserDatum;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.core.services.account.AccountCoreService;
import cn.nitrowater.core.services.auth.SingleUseTokenService;
import cn.nitrowater.core.services.auth.code.VerificationService;
import cn.nitrowater.core.services.user.UserCoreService;
import cn.nitrowater.core.services.user.UserDatumCoreService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

    private static final String FP_CTX_PREFIX = "op:verify:context:fp:";
    private static final java.time.Duration FP_CTX_TTL = Duration.ofMinutes(5);


    @Transactional
    @Override
    public void changePwd(long userUid, String newPwd, String confirmPwd, DeviceInfo deviceInfo) {
        if (!newPwd.equals(confirmPwd)) {
            throw new BizException(BaseResponseCode.PASSWORD_TWO_PASSWORD_NOT_EQUAL);
        }
        userCoreService.changePwd(userUid, newPwd);
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
        }

    // -- Email operations --

    @Override
    @Transactional
    public void activateEmail(long userUid, String email, DeviceInfo deviceInfo) {
        UserDatum ud = userDatumCoreService.saveNewEmail(userUid, email, true);
        userDatumRepo.save(ud);
        }

    @Override
    @Transactional
    public CodeResult changeEmail(long userUid, String newEmail, DeviceInfo deviceInfo) {
        userDatumCoreService.saveNewEmail(userUid, newEmail, false);
        UserDatum ud = userDatumCoreService.getUserDatum(userUid);
        ud.setEmailExpireAt(Instant.now().plus(Duration.ofHours(24)));
        userDatumRepo.save(ud);
        return verificationService.sendCodeForAuthenticated(
                newEmail, VerifyChannel.EMAIL, VerifyScene.ACTIVATE);
    }

    @Override
    @Transactional
    public void verifyChangeEmail(String verifyKey, String code, long userUid, DeviceInfo deviceInfo) {
        String email = userDatumCoreService.getRawEmail(userUid);
        if (email == null) {
            throw new BizException(BaseResponseCode.EMAIL_NOT_FOUND);
        }
        verificationService.verifyCode(email, VerifyScene.ACTIVATE, VerifyChannel.EMAIL, verifyKey, code);
        UserDatum ud = userDatumCoreService.getUserDatum(userUid);
        ud.setEmailVerified(true);
        userDatumRepo.save(ud);
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
        userDatumCoreService.saveNewPhone(userUid, newPhone, false);
        return verificationService.sendCodeForAuthenticated(
                newPhone, VerifyChannel.SMS, VerifyScene.ACTIVATE);
    }

    @Override
    @Transactional
    public void verifyChangePhone(String verifyKey, String code, long userUid, DeviceInfo deviceInfo) {
        String phone = userDatumCoreService.getRawPhone(userUid);
        if (phone == null) {
            throw new BizException(BaseResponseCode.NOT_FOUND);
        }
        verificationService.verifyCode(phone, VerifyScene.ACTIVATE, VerifyChannel.SMS, verifyKey, code);
        UserDatum ud = userDatumCoreService.getUserDatum(userUid);
        ud.setPhoneVerified(true);
        userDatumRepo.save(ud);
        }

    @Override
    @Transactional
    public void activatePhone(long userUid, String phone, DeviceInfo deviceInfo) {
        userDatumCoreService.saveNewPhone(userUid, phone, true);
        }

    // -- Unbind --

    @Override
    @Transactional
    public void unbindEmail(long userUid, String email, DeviceInfo deviceInfo) {
        String emailRaw = userDatumCoreService.getRawEmail(userUid);
        if (emailRaw == null) {
            throw new BizException(BaseResponseCode.EMAIL_NOT_FOUND);
        }
        if (!emailRaw.equals(email)) {
            throw new BizException(BaseResponseCode.EMAIL_INVALID);
        }
        UserDatum ud = userDatumCoreService.getUserDatum(userUid);
        ud.setEmailExpireAt(null);
        ud.setEmailEncrypted(null);
        ud.setEmailVerified(false);
        userDatumRepo.save(ud);
        }

    @Override
    @Transactional
    public void resetPasswordByToken(Long userUid, String newPwd, String confirmPwd, DeviceInfo deviceInfo) {
        if (!newPwd.equals(confirmPwd)) {
            throw new BizException(BaseResponseCode.PASSWORD_TWO_PASSWORD_NOT_EQUAL);
        }
        userCoreService.changePwd(userUid, newPwd);
        }

    @Override
    @Nullable
    public String initiateForgotPasswordReAuth(String identifier) {
        try {
            Long uid = userCoreService.resolveUid(identifier);
            String phone = userDatumCoreService.getRawPhone(uid);
            if (phone == null) {
                return null;
            }
            CodeResult result = verificationService.sendCodeForAuthenticated(phone, VerifyChannel.SMS, VerifyScene.FORGOT_PASSWORD);
            String reAuthKey = result.getKey();
            redisHelperHolder.set(FP_CTX_PREFIX + reAuthKey, phone, FP_CTX_TTL);
            return reAuthKey;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Nullable
    public TokenResult verifyForgotPasswordReAuth(String reAuthKey, String code) {
        String phone = redisHelperHolder.getValue(FP_CTX_PREFIX + reAuthKey);
        if (phone == null) return null;
        verificationService.verifyCode(phone, VerifyScene.FORGOT_PASSWORD, VerifyChannel.SMS,
                reAuthKey, code);
        // Code verified — now delete the key (only on success, allowing retry on wrong code)
        redisHelperHolder.del(FP_CTX_PREFIX + reAuthKey);
        Long uid = userCoreService.resolveUid(phone);
        return singleUseTokenService.generateVerifyToken(VerifyScene.FORGOT_PASSWORD.name(), uid);
    }
}
