package cn.nitrowater.core.application.auth.impl;

import cn.nitrowater.core.infrastructure.domain.user.AccountStatus;
import cn.nitrowater.core.infrastructure.domain.user.User;
import cn.nitrowater.core.infrastructure.domain.user.UserDatum;
import cn.nitrowater.core.infrastructure.domain.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.api.AuthCode;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.common.exceptions.AuthException;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.lib.utils.UidGenerator;
import cn.nitrowater.lib.utils.codec.HashUtil;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.RegisterRequest;
import cn.nitrowater.core.api.req.auth.VerifyCodeDto;
import cn.nitrowater.core.infrastructure.domain.EncryptionDataKey;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.exception.RegisterChannelUnsupportedException;
import cn.nitrowater.core.exception.UserNameAlreadyExistException;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.core.infrastructure.security.EncryptedKeyService;
import cn.nitrowater.core.infrastructure.security.EncryptionHelper;
import cn.nitrowater.core.application.auth.AuthIdentifierUtil;
import cn.nitrowater.core.application.auth.RegisterService;
import cn.nitrowater.core.application.auth.code.VerificationService;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {
    private final UserRepository userRepo;
    private final UserDatumRepo userDatumRepo;
    private final EncryptedKeyService encryptedKeyService;
    private final UidGenerator uidGenerator;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final VerificationService verificationService;

    @Value("${expiresIn.email.unverified-expiresIn-hours:24}")
    private Long emailUnverifiedExpireHours;

    @Transactional
    @Override
    public User register(RegisterRequest body, String codeKey) {
        // Bean validation enforces exactly one identifier; keep a defensive branch for direct
        // service calls so a bad payload can never create a half-registered account.
        boolean byEmail = StringUtil.isNotBlank(body.getEmail());
        String target = byEmail
                ? AuthIdentifierUtil.normalizeEmail(body.getEmail())
                : AuthIdentifierUtil.normalizePhone(body.getPhone());
        VerifyChannel channel = byEmail ? VerifyChannel.EMAIL : VerifyChannel.SMS;

        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();
        String hmacSecret = hmacKey.getEncryptedKey();
        String targetHash = HashUtil.toSha256HmacString(target, hmacSecret);

        userRepo.findByUsername(body.getUsername()).ifPresent(_ -> {
            throw new UserNameAlreadyExistException();
        });

        // Uniqueness first: a taken target should fail precisely, before a valid code is consumed.
        // The unique indexes in the V1 baseline remain the final race-safe guard.
        if (byEmail) {
            userDatumRepo.findByEmailHash(targetHash).ifPresent(
                    _ -> {
                        throw new BizException(BaseResponseCode.EMAIL_ALREADY_USED);
                    }
            );
        } else {
            userDatumRepo.findByPhoneHash(targetHash).ifPresent(
                    _ -> {
                        throw new BizException(BaseResponseCode.PHONE_NUMBER_ALREADY_USED);
                    }
            );
        }

        // Proof: the cookie selected by the controller belongs to this channel and target.
        VerifyCodeDto verify = body.getVerify();
        String proofTarget = verify == null ? null : (byEmail
                ? AuthIdentifierUtil.normalizeEmail(verify.getTarget())
                : AuthIdentifierUtil.normalizePhone(verify.getTarget()));
        if (verify == null || verify.getChannel() != channel || !target.equals(proofTarget)) {
            throw new AuthException(AuthCode.REAUTHORIZATION_REQUIRED);
        }
        verificationService.verifyCode(target, VerifyScene.REGISTER, channel, codeKey, verify.getCode());

        // Encrypt the proven target and the optional password.
        EncryptionDataKey aesKey = encryptedKeyService.getAesKey();
        String encryptedTarget = EncryptionHelper.encryptField(target, aesKey);
        String password = body.getPassword();

        // STEP 5: Set user
        User user = new User();
        user.setUsername(body.getUsername());
        if (StringUtil.isNotBlank(password)) {
            user.setPasswordHash(encoder.encode(password));
        }
        user.setUid(uidGenerator.generateUid());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setUserType(UserType.COMMON);
        // STEP 6: Set user data
        UserDatum ud = new UserDatum();
        ud.setUser(user);
        ud.setUid(user.getUid());
        ud.setEncryptionKeyId(aesKey.getKeyId());
        ud.setEmailVerified(false);
        ud.setPhoneVerified(false);
        if (byEmail) {
            ud.setEmailEncrypted(encryptedTarget);
            ud.setEmailHash(targetHash);
            ud.setEmailVerified(true);
        } else {
            ud.setPhoneEncrypted(encryptedTarget);
            ud.setPhoneHash(targetHash);
            ud.setPhoneVerified(true);
        }

        // TODO(SSO): UserProfile/UserCounter/UserPreference/UserSetting bootstrap cut — business tables not migrated
        user.setUserDatum(ud);
        userRepo.save(user);
        // TODO(SSO): stats recording / audit logging cut — business services not migrated
        return user;
    }

    @Transactional
    @Override
    public User autoRegister(String target, VerifyChannel channel, VerifyScene scene, String codeKey, String code, String deviceFp) {
        if (channel == VerifyChannel.EMAIL) {
            throw new RegisterChannelUnsupportedException();
        }

        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();

        verificationService.verifyCode(target, scene, channel, codeKey, code);

        if (channel == VerifyChannel.SMS) {
            userDatumRepo.findByPhoneHash(HashUtil.toSha256HmacString(target, hmacKey.getEncryptedKey())).ifPresent(
                    _ -> { throw new BizException(BaseResponseCode.PHONE_NUMBER_ALREADY_USED); }
            );
        } else if (channel == VerifyChannel.EMAIL) {
            userDatumRepo.findByEmailHash(HashUtil.toSha256HmacString(target, hmacKey.getEncryptedKey())).ifPresent(
                    _ -> { throw new BizException(BaseResponseCode.EMAIL_ALREADY_USED); }
            );
        }

        String username;
        int suffix = 0;
        do {
            String shortHash = HashUtil.toSha256HmacString(target, hmacKey.getEncryptedKey()).substring(0, 8);
            String suffixStr = suffix > 0 ? String.valueOf(suffix) : "";
            username = "u_" + shortHash + suffixStr;
            suffix++;
        } while (userRepo.findByUsername(username).isPresent());

        EncryptionDataKey aesKet = encryptedKeyService.getAesKey();
        String encryptedTarget = EncryptionHelper.encryptField(target, aesKet);

        User user = new User();
        user.setUsername(username);
        user.setUid(uidGenerator.generateUid());
        user.setAccountStatus(AccountStatus.ACTIVE);

        UserDatum ud = new UserDatum();
        ud.setUser(user);
        ud.setUid(user.getUid());
        ud.setEncryptionKeyId(aesKet.getKeyId());

        if (channel == VerifyChannel.SMS) {
            ud.setPhoneEncrypted(encryptedTarget);
            ud.setPhoneHash(HashUtil.toSha256HmacString(target, hmacKey.getEncryptedKey()));
            ud.setPhoneVerified(true);
        } else {
            ud.setEmailEncrypted(encryptedTarget);
            ud.setEmailHash(HashUtil.toSha256HmacString(target, hmacKey.getEncryptedKey()));
            ud.setEmailVerified(false);
            ud.setEmailExpireAt(Instant.now().plus(Duration.ofHours(emailUnverifiedExpireHours)));
        }

        // TODO(SSO): UserProfile/UserCounter/UserPreference/UserSetting bootstrap cut — business tables not migrated
        user.setUserDatum(ud);
        userRepo.save(user);
        // TODO(SSO): stats recording / audit logging cut — business services not migrated
        return user;
    }
}
