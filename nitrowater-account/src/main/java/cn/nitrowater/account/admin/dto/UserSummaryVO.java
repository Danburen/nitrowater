package cn.nitrowater.account.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * Account row for the admin user list.
 *
 * @param uid           account id
 * @param username      login name
 * @param nickname      display name
 * @param accountStatus account status enum name (ACTIVE / SUSPENDED / DEACTIVATED)
 * @param userType      account type enum name (COMMON / ADMIN / ...)
 * @param roles         bound role codes (empty when none)
 * @param createdAt     registration timestamp
 */
public record UserSummaryVO(
        long uid,
        String username,
        String nickname,
        String accountStatus,
        String userType,
        List<String> roles,
        Instant createdAt) {
}
