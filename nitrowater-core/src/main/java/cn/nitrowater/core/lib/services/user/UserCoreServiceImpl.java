package cn.nitrowater.core.lib.services.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.utils.codec.HashUtil;
import cn.nitrowater.core.lib.entity.EncryptionDataKey;
import cn.nitrowater.core.lib.entity.user.User;
import cn.nitrowater.core.lib.entity.user.UserDatum;
import cn.nitrowater.core.lib.exception.BizException;
import cn.nitrowater.core.lib.exception.notfound.NotFoundException;
import cn.nitrowater.core.lib.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.lib.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.core.lib.infrastructure.security.EncryptedKeyService;

/**
 * SSO-scope implementation of {@link UserCoreService}.
 * <p>Copied and trimmed from waterfun: role/permission/avatar/nickname business cuts applied.</p>
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserCoreServiceImpl implements UserCoreService {
    private final UserRepository userRepository;
    private final UserDatumRepo userDatumRepo;
    private final EncryptedKeyService encryptedKeyService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public User changePwd(long userUid, String newPwd) {
        User u = getUser(userUid);
        // Allow setting password for users who registered without one (e.g., via SMS code)
        if (u.getPasswordHash() != null && encoder.matches(newPwd, u.getPasswordHash())) {
            throw new BizException(BaseResponseCode.PASSWORD_TWO_PASSWORD_MUST_DIFFERENT);
        }
        u.setPasswordHash(encoder.encode(newPwd));
        return userRepository.save(u);
    }

    @Override
    public User getUser(long userUid) {
        return userRepository.findUserByUid(userUid)
                .orElseThrow(() -> new NotFoundException("User UID: " + userUid));
    }

    @Override
    public Long resolveUid(String identifier) {
        // 1. Try phone (HMAC hash)
        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();
        String phoneHash = HashUtil.toSha256HmacString(identifier, hmacKey.getEncryptedKey());
        UserDatum ud = userDatumRepo.findByPhoneHash(phoneHash).orElse(null);
        if (ud != null) return ud.getUid();

        // 2. Try email (HMAC hash)
        String emailHash = HashUtil.toSha256HmacString(identifier, hmacKey.getEncryptedKey());
        ud = userDatumRepo.findByEmailHash(emailHash).orElse(null);
        if (ud != null) return ud.getUid();

        // 3. Try username
        User u = userRepository.findByUsername(identifier).orElse(null);
        if (u != null) return u.getUid();

        throw new NotFoundException("User not found for identifier: " + identifier);
    }
}
