package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import java.util.Collection;

/**
 * Cleans up every server-side token set during logout and hands the logout success handler a
 * fresh {@code id_token} for RP-Initiated Logout.
 *
 * <p><strong>Why a refresh is required.</strong> The Authorization Server re-mints the
 * {@code id_token} on every access-token refresh and replaces the stored value, so the
 * {@code id_token} captured at login is no longer resolvable by {@code /connect/logout} (the AS
 * would answer {@code 400 invalid_token}). Refreshing the active account here yields the
 * <em>current</em> {@code id_token}; because the BFF uses non-reused refresh tokens it also
 * rotates the refresh token.</p>
 *
 * <p><strong>Multi-account cleanup.</strong> Every account in the session is visited. The active
 * account is refreshed (to obtain its fresh {@code id_token}) and its rotated refresh token is
 * revoked; inactive accounts have their stored refresh token revoked directly. If the account
 * that owns the AS session was removed earlier, its fresh {@code id_token} was stashed in the
 * pool before its refresh token was revoked; that hint is preferred and every remaining refresh
 * token is revoked directly. The access token is a self-contained JWT that the resource server
 * validates offline against JWKS, so revoking it has no effect there and is intentionally skipped
 * (it expires on its own, short TTL).</p>
 *
 * <p>This handler runs while the session is still alive (before the default
 * {@code SecurityContextLogoutHandler} invalidates it). Every upstream call is best-effort: a
 * failure is logged and logout still proceeds locally.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class OidcSessionLogoutHandler implements LogoutHandler {

    /** Request attribute carrying the freshly minted {@code id_token} for the success handler. */
    public static final String FRESH_ID_TOKEN_ATTRIBUTE =
            OidcSessionLogoutHandler.class.getName() + ".FRESH_ID_TOKEN";

    private final MultiAccountAuthorizedClientRepository authorizedClientRepository;
    private final UpstreamTokenService upstream;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        // If the AS session owner was removed earlier, its fresh id_token was stashed in the pool
        // before its refresh token was revoked. Prefer that hint: the active account's id_token
        // carries a different sid and the AS would reject it.
        String logoutHint = logoutHint(request);
        if (logoutHint != null) {
            request.setAttribute(FRESH_ID_TOKEN_ATTRIBUTE, logoutHint);
        }
        Collection<OAuth2AuthorizedClient> clients = authorizedClientRepository.loadAll(request);
        if (clients.isEmpty()) {
            log.debug("Logout: no server-side authorized clients in session; nothing to revoke");
            return;
        }
        for (OAuth2AuthorizedClient client : clients) {
            if (client.getRefreshToken() == null) {
                continue;
            }
            String refreshToken = client.getRefreshToken().getTokenValue();
            if (logoutHint == null && client.getPrincipalName().equals(authentication.getName())) {
                String freshIdToken = upstream.refreshAndRevoke(refreshToken);
                if (freshIdToken != null) {
                    request.setAttribute(FRESH_ID_TOKEN_ATTRIBUTE, freshIdToken);
                }
            } else {
                upstream.revoke(refreshToken);
            }
        }
        authorizedClientRepository.removeAll(request);
    }

    private static String logoutHint(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null
                && session.getAttribute(AccountPool.SESSION_ATTRIBUTE) instanceof AccountPool pool) {
            return pool.logoutHintIdToken();
        }
        return null;
    }
}
