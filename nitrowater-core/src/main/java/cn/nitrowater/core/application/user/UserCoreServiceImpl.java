package cn.nitrowater.core.application.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.utils.codec.HashUtil;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.application.auth.AuthIdentifierUtil;
import cn.nitrowater.core.domain.EncryptionDataKey;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.domain.user.UserDatum;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.exception.notfound.NotFoundException;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import cn.nitrowater.core.infrastructure.security.EncryptedKeyService;

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
        if (identifier == null || identifier.isBlank()) {
            throw new NotFoundException("Blank login identifier");
        }
        String value = identifier.trim();
        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();
        String key = hmacKey.getEncryptedKey();

        if (AuthIdentifierUtil.isEmail(value)) {
            String emailHash = HashUtil.toSha256HmacString(AuthIdentifierUtil.normalizeEmail(value), key);
            return userDatumRepo.findByEmailHash(emailHash)
                    .map(UserDatum::getUid)
                    .orElseThrow(() -> new NotFoundException("User not found for email: " + value));
        }
        if (AuthIdentifierUtil.isPhone(value)) {
            String phoneHash = HashUtil.toSha256HmacString(AuthIdentifierUtil.normalizePhone(value), key);
            return userDatumRepo.findByPhoneHash(phoneHash)
                    .map(UserDatum::getUid)
                    .orElseThrow(() -> new NotFoundException("User not found for phone: " + value));
        }
        return userRepository.findByUsername(value)
                .map(User::getUid)
                .orElseThrow(() -> new NotFoundException("User not found for username: " + value));
    }

    @Override
    public Long resolveUidByTarget(String target, VerifyChannel channel) {
        if (target == null || target.isBlank()) {
            throw new NotFoundException("Blank verification target");
        }
        EncryptionDataKey hmacKey = encryptedKeyService.getUserDatumHmacKey();
        String key = hmacKey.getEncryptedKey();
        String hash = switch (channel) {
            case SMS -> HashUtil.toSha256HmacString(AuthIdentifierUtil.normalizePhone(target), key);
            case EMAIL -> HashUtil.toSha256HmacString(AuthIdentifierUtil.normalizeEmail(target), key);
        };
        UserDatum ud = switch (channel) {
            case SMS -> userDatumRepo.findByPhoneHash(hash).orElse(null);
            case EMAIL -> userDatumRepo.findByEmailHash(hash).orElse(null);
        };
        if (ud == null) {
            throw new NotFoundException("No account bound to the verification target");
        }
        return ud.getUid();
    }
}
