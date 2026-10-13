package cn.nitrowater.account.web.admin;

import cn.nitrowater.account.security.SsoUserPrincipal;
import org.springframework.security.core.Authentication;

/**
 * Small helper to pull the SSO principal out of the current {@link Authentication} in the admin
 * console controllers. The security chain already guarantees {@code ROLE_SUPER_ADMIN} for
 * {@code /api/admin/**}, so reaching here without a principal is a programming error.
 */
public final class AdminPrincipals {

    private AdminPrincipals() {
    }

    /** Returns the authenticated {@link SsoUserPrincipal}. */
    public static SsoUserPrincipal require(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SsoUserPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("Admin console requires an authenticated SSO principal");
    }
}
