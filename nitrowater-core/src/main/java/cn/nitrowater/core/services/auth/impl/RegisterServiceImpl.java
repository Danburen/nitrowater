package cn.nitrowater.core.services.auth.impl;

import cn.nitrowater.core.entity.user.AccountStatus;
import cn.nitrowater.core.entity.user.User;
import cn.nitrowater.core.entity.user.UserDatum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.api.AuthCode;
import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.common.exceptions.AuthException;
import cn.nitrowater.core.utils.StringUtil;
import cn.nitrowater.core.utils.UidGenerator;
import cn.nitrowater.core.utils.codec.HashUtil;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.RegisterRequest;
import cn.nitrowater.core.api.req.auth.VerifyCodeDto;
import cn.nitrowater.core.entity.EncryptionDataKey;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.exception.RegisterChannelUnsupportedException;
import cn.nitrowater.core.exception.UserNameAlreadyExistException;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.core.infrastructure.security.EncryptedKeyService;
import cn.nitrowater.core.infrastructure.security.EncryptionHelper;
import cn.nitrowater.core.services.auth.RegisterService;
import cn.nitrowater.core.services.auth.code.VerificationService;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {
    private final AuthCoreServiceImpl authService;
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
    public User register(RegisterRequest body, String smsCodeKey) {
        String email = body.getEmail();
        String phone = body.getPhone();
        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();

        userRepo.findByUsername(body.getUsername()).ifPresent(_ -> {
            throw new UserNameAlreadyExistException();
        });
        // Verify phone
        VerifyCodeDto verify = body.getVerify();
        if(! verify.getTarget().equals(phone)){
            throw new AuthException(AuthCode.REAUTHORIZATION_REQUIRED);
        }
        verificationService.verifyCode(smsCodeKey, verify);

        userDatumRepo.findByPhoneHash(HashUtil.toSha256HmacString(phone, hmacKey.getEncryptedKey())).ifPresent(
                _->{
                    throw new BizException(BaseResponseCode.PHONE_NUMBER_ALREADY_USED);
                }
        );
        // Verify email
        if (StringUtil.isNotBlank(email)) {
            userDatumRepo.findByEmailHash(HashUtil.toSha256HmacString(email, hmacKey.getEncryptedKey())).ifPresent(
                    _ -> {
                        throw new BizException(BaseResponseCode.EMAIL_ALREADY_USED);
                    }
            );
        }
        // Encrypt email, phone, password
        EncryptionDataKey aesKet = encryptedKeyService.getAesKey();
        String encryptedPhone = EncryptionHelper.encryptField(phone, aesKet);
        String password = body.getPassword();

        // STEP 5: Set user
        User user = new User();
        user.setUsername(body.getUsername());
        if(StringUtil.isNotBlank( password)) user.setPasswordHash(encoder.encode(password));
        user.setUid(uidGenerator.generateUid());
        user.setAccountStatus(AccountStatus.ACTIVE);
        // STEP 6: Set user data
        UserDatum ud = new UserDatum();
        ud.setUser(user);
        ud.setUid(user.getUid());
        ud.setEncryptionKeyId(aesKet.getKeyId());
        ud.setPhoneEncrypted(encryptedPhone);
        ud.setPhoneHash(HashUtil.toSha256HmacString(phone, hmacKey.getEncryptedKey()));

        if(StringUtil.isNotBlank(email)) {
            String encryptedEmail = EncryptionHelper.encryptField(email, aesKet);
            ud.setEmailEncrypted(encryptedEmail);
            ud.setEmailHash(HashUtil.toSha256HmacString(email, hmacKey.getEncryptedKey()));
            ud.setEmailExpireAt(Instant.now().plus(Duration.ofHours(emailUnverifiedExpireHours)));
        }

        ud.setPhoneVerified(true);
        ud.setEmailVerified(false);

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
