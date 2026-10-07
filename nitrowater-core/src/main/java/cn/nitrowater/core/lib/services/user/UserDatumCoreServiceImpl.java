package cn.nitrowater.core.lib.services.user;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.utils.MaskUtil;
import cn.nitrowater.core.lib.utils.codec.HashUtil;
import cn.nitrowater.core.lib.api.resp.AccountResp;
import cn.nitrowater.core.lib.entity.EncryptionDataKey;
import cn.nitrowater.core.lib.entity.user.UserDatum;
import cn.nitrowater.core.lib.exception.BizException;
import cn.nitrowater.core.lib.exception.PhoneNumberAlreadyUsedException;
import cn.nitrowater.core.lib.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.lib.infrastructure.security.EncryptedKeyService;
import cn.nitrowater.core.lib.infrastructure.security.EncryptionHelper;

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
        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();
        String newHashed = HashUtil.toSha256HmacString(email, hmacKey.getEncryptedKey());
        // check new email whether equal to old email
        if(newHashed.equals(ud.getEmailHash())){
            throw new BizException(BaseResponseCode.TWO_VALUE_MUST_DIFFERENT,"email");
        }
        // check new email whether bound by others
        userDatumRepo.findByEmailHash(newHashed).ifPresent(_ ->{
            throw new BizException(BaseResponseCode.EMAIL_ALREADY_USED);
        });
        // save to the db
        EncryptionDataKey aesKey = encryptedKeyService.getAesKey();
        ud.setEmailEncrypted(EncryptionHelper.encryptField(email, aesKey));
        ud.setEmailHash(newHashed);
        ud.setEmailVerified(verified);
        ud.setEncryptionKeyId(aesKey.getKeyId());
        return userDatumRepo.save(ud);
    }

    @Override
    @Transactional
    public UserDatum saveNewPhone(long userUid, String phone, boolean verified) {
        UserDatum ud = getUserDatum(userUid);
        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();
        String newHashed = HashUtil.toSha256HmacString(phone, hmacKey.getEncryptedKey());
        // check new phone whether equal to old phone
        if(newHashed.equals(ud.getPhoneHash())){
            throw new BizException(BaseResponseCode.TWO_VALUE_MUST_DIFFERENT,"phone");
        }
        // check new phone whether bound by others
        userDatumRepo.findByPhoneHash(newHashed).ifPresent(_ ->{
            throw new PhoneNumberAlreadyUsedException();
        });
        // save to the db
        EncryptionDataKey aesKey = encryptedKeyService.getAesKey();
        ud.setPhoneEncrypted(EncryptionHelper.encryptField(phone, aesKey));
        ud.setPhoneHash(newHashed);
        ud.setPhoneVerified(verified);
        ud.setEncryptionKeyId(aesKey.getKeyId());
        return userDatumRepo.save(ud);
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
