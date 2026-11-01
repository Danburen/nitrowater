package cn.nitrowater.core.application.user;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.utils.MaskUtil;
import cn.nitrowater.lib.utils.codec.HashUtil;
import cn.nitrowater.core.api.resp.AccountResp;
import cn.nitrowater.core.application.auth.AuthIdentifierUtil;
import cn.nitrowater.core.domain.EncryptionDataKey;
import cn.nitrowater.core.domain.user.UserDatum;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.exception.PhoneNumberAlreadyUsedException;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.security.EncryptedKeyService;
import cn.nitrowater.core.infrastructure.security.EncryptionHelper;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserDatumCoreServiceImpl implements UserDatumCoreService {
    private final UserDatumRepo userDatumRepo;
    private final EncryptedKeyService encryptedKeyService;

    @Override
    public UserDatum getUserDatum(long userUid) {
        return userDatumRepo.findUserDatumByUserUid(userUid)
                .orElseThrow(() -> new BizException(BaseResponseCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserDatum saveNewEmail(long userUid, String email, boolean verified) {
        UserDatum ud = getUserDatum(userUid);
        String normalized = AuthIdentifierUtil.normalizeEmail(email);
        String newHashed = hashTarget(normalized);
        validateEmailHash(ud, newHashed);
        EncryptionDataKey aesKey = encryptedKeyService.getAesKey();
        ud.setEmailEncrypted(EncryptionHelper.encryptField(normalized, aesKey));
        ud.setEmailHash(newHashed);
        ud.setEmailVerified(verified);
        ud.setEncryptionKeyId(aesKey.getKeyId());
        return userDatumRepo.save(ud);
    }

    @Override
    public void validateEmailAvailable(long userUid, String email) {
        UserDatum ud = getUserDatum(userUid);
        validateEmailHash(ud, hashTarget(AuthIdentifierUtil.normalizeEmail(email)));
    }

    @Override
    @Transactional
    public void markEmailVerified(long userUid) {
        UserDatum ud = getUserDatum(userUid);
        if (ud.getEmailEncrypted() == null) {
            throw new BizException(BaseResponseCode.EMAIL_NOT_FOUND);
        }
        ud.setEmailVerified(true);
        ud.setEmailExpireAt(null);
        userDatumRepo.save(ud);
    }

    @Override
    @Transactional
    public UserDatum saveNewPhone(long userUid, String phone, boolean verified) {
        UserDatum ud = getUserDatum(userUid);
        String normalized = AuthIdentifierUtil.normalizePhone(phone);
        String newHashed = hashTarget(normalized);
        validatePhoneHash(ud, newHashed);
        EncryptionDataKey aesKey = encryptedKeyService.getAesKey();
        ud.setPhoneEncrypted(EncryptionHelper.encryptField(normalized, aesKey));
        ud.setPhoneHash(newHashed);
        ud.setPhoneVerified(verified);
        ud.setEncryptionKeyId(aesKey.getKeyId());
        return userDatumRepo.save(ud);
    }

    @Override
    public void validatePhoneAvailable(long userUid, String phone) {
        UserDatum ud = getUserDatum(userUid);
        validatePhoneHash(ud, hashTarget(AuthIdentifierUtil.normalizePhone(phone)));
    }

    @Override
    @Transactional
    public void markPhoneVerified(long userUid) {
        UserDatum ud = getUserDatum(userUid);
        if (ud.getPhoneEncrypted() == null) {
            throw new BizException(BaseResponseCode.NOT_FOUND);
        }
        ud.setPhoneVerified(true);
        userDatumRepo.save(ud);
    }

    private String hashTarget(String target) {
        return HashUtil.toSha256HmacString(target, encryptedKeyService.getUserDatumHmacKey().getEncryptedKey());
    }

    private void validateEmailHash(UserDatum ud, String hash) {
        if (hash.equals(ud.getEmailHash())) {
            throw new BizException(BaseResponseCode.TWO_VALUE_MUST_DIFFERENT, "email");
        }
        userDatumRepo.findByEmailHash(hash).ifPresent(_ -> {
            throw new BizException(BaseResponseCode.EMAIL_ALREADY_USED);
        });
    }

    private void validatePhoneHash(UserDatum ud, String hash) {
        if (hash.equals(ud.getPhoneHash())) {
            throw new BizException(BaseResponseCode.TWO_VALUE_MUST_DIFFERENT, "phone");
        }
        userDatumRepo.findByPhoneHash(hash).ifPresent(_ -> {
            throw new PhoneNumberAlreadyUsedException();
        });
    }

    @Override
    public String getRawPhone(long userUid) {
        UserDatum ud = getUserDatum(userUid);
        return EncryptionHelper.decryptField(
                ud.getPhoneEncrypted(),
                encryptedKeyService.getKeyById(ud.getEncryptionKeyId())
        );
    }

    @Override
    public @Nullable String getRawEmail(long userUid) {
        UserDatum ud = getUserDatum(userUid);
        String emailEncrypted = ud.getEmailEncrypted();
        if(emailEncrypted == null){
            return null;
        }
        return EncryptionHelper.decryptField(
                ud.getEmailEncrypted(),
                encryptedKeyService.getKeyById(ud.getEncryptionKeyId())
        );
    }

    @Override
    @Transactional
    public AccountResp getAccountInfo(long userUid) {
        UserDatum ud = userDatumRepo.findUserDatumByUserUid(userUid)
                .orElseThrow(() -> new BizException(BaseResponseCode.USER_NOT_FOUND));
        EncryptionDataKey aesKey = encryptedKeyService.getKeyById(ud.getEncryptionKeyId());
        String realEmail = ud.getEmailEncrypted() != null
                ? EncryptionHelper.decryptField(ud.getEmailEncrypted(), aesKey) : null;
        String realPhone = ud.getPhoneEncrypted() != null
                ? EncryptionHelper.decryptField(ud.getPhoneEncrypted(), aesKey) : null;
        return new AccountResp(
                realPhone != null ? MaskUtil.maskPhone(realPhone) : null,
                realEmail != null ? MaskUtil.maskEmail(realEmail) : null,
                ud.getPhoneVerified(),
                ud.getEmailVerified()
        );
    }

}
