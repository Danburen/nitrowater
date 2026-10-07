package cn.nitrowater.account.security;

import cn.nitrowater.core.infrastructure.persistence.role.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 2.5: reads role codes bound to an account.
 */
@Component
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;

    /** Returns the account's bound role codes, ordered by code. Empty when none. */
    public List<String> findRoleCodes(long uid) {
        return userRoleRepository.findRoleCodesByUid(uid);
    }
}
