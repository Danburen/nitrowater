package cn.nitrowater.server.service;

import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.repository.BizUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Resolves (and lazily creates) the business-side user mirror for an authenticated caller.
 *
 * <p>account is the identity authority; this service projects the JWT into {@code biz_user}:
 * {@code uid} + profile snapshot (username/nickname/avatar). The avatar is derived from the OIDC
 * {@code picture} claim (its trailing path segment is the resource uuid).</p>
 */
@Service
public class BizUserService {

    private final BizUserRepository repository;

    public BizUserService(BizUserRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the mirror for the caller, creating it on first touch.
     *
     * @param jwt the caller's access token
     * @return the (existing or new) business user row
     */
    @Transactional
    public BizUser resolve(Jwt jwt) {
        Long uid = uidOf(jwt);
        return repository.findByUid(uid).orElseGet(() -> create(uid, jwt));
    }

    /** @return the caller's numeric uid (the {@code uid} claim, falling back to {@code sub}) */
    public static Long uidOf(Jwt jwt) {
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) {
            uid = jwt.getSubject();
        }
        if (uid == null || uid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token carries no uid");
        }
        try {
            return Long.valueOf(uid);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token uid is not numeric");
        }
    }

    private BizUser create(Long uid, Jwt jwt) {
        BizUser user = new BizUser();
        user.setUid(uid);
        user.setUsername(jwt.getClaimAsString("preferred_username"));
        user.setNickname(jwt.getClaimAsString("name"));
        user.setAvatarResourceUuid(avatarUuidOf(jwt.getClaimAsString("picture")));
        user.setProfileSyncedAt(Instant.now());
        return repository.save(user);
    }

    /** @return the resource uuid embedded in an OIDC {@code picture} URL, or {@code null} */
    static String avatarUuidOf(String picture) {
        if (picture == null || picture.isBlank()) {
            return null;
        }
        int slash = picture.lastIndexOf('/');
        String candidate = slash < 0 ? picture : picture.substring(slash + 1);
        return candidate.isBlank() ? null : candidate;
    }
}
