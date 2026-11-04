package cn.nitrowater.core.infrastructure.persistence.specification;

import cn.nitrowater.core.domain.role.UserRole;
import cn.nitrowater.core.domain.user.AccountStatus;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.domain.user.UserType;
import cn.nitrowater.lib.utils.StringUtil;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA filters for the account listing.
 *
 * <p>Every dimension is optional; blank values are ignored so a caller can pass request
 * parameters straight through.</p>
 */
public final class UserSpec {

    private UserSpec() {
    }

    /**
     * Matches accounts by keyword, status, account type and bound role.
     *
     * <p>All values are taken as-is: normalizing the role code (trim, upper-case, dropping the
     * {@code ROLE_} prefix) and resolving defaults is the caller's business rule, not a query
     * concern.</p>
     *
     * @param keyword optional username / nickname / uid substring
     * @param status optional account-status filter
     * @param userType optional account-type filter
     * @param roleCode optional normalized role code the account must hold
     * @return the combined specification
     */
    public static Specification<User> of(String keyword, AccountStatus status, UserType userType,
            String roleCode) {
        SpecificationBuilder<User> builder = SpecificationBuilder.<User>builder()
                .likeIgnoreCaseAny(keyword, "username", "nickname", "uid")
                .eq("accountStatus", status)
                .eq("userType", userType);
        if (StringUtil.isNotBlank(roleCode)) {
            String role = roleCode.trim();
            builder.where((root, query, cb) -> {
                Subquery<Long> bound = query.subquery(Long.class);
                Root<UserRole> binding = bound.from(UserRole.class);
                bound.select(binding.get("uid")).where(cb.equal(binding.get("role").get("code"), role));
                return root.get("uid").in(bound);
            });
        }
        return builder.build();
    }
}
