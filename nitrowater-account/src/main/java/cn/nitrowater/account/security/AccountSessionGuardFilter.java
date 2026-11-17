package cn.nitrowater.account.security;

import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Enforces the two limits the SSO session itself must honour, on the non-OIDC chain.
 *
 * <p>{@link PromptLoginFilter} only runs on {@code /oauth2/authorize}, so without this filter the
 * admin console and the account APIs would slide on the 7-day idle timeout forever: an admin who
 * clicks once a week could keep the session alive indefinitely. This filter applies the same rules
 * to every session-authenticated request:</p>
 *
 * <ol>
 *   <li><b>Revocation epoch</b> — rotating {@code nitrowater:auth:session-epoch} rejects every
 *       session created before the rotation, even if the best-effort Redis scan in
 *       {@code SessionRevocationService} missed one.</li>
 *   <li><b>Absolute age</b> — the instant the credentials were proved must be younger than
 *       {@code app.session.absolute}; idle activity does not extend it.</li>
 * </ol>
 *
 * <p>Bearer-token requests are skipped: a JWT carries its own expiry and was never a browser
 * session. Rejections invalidate the session and clear the context; the chain's entry point then
 * answers 401 JSON for {@code /api/**} and a login redirect for HTML pages.</p>
 */
public class AccountSessionGuardFilter extends OncePerRequestFilter {

    /** Session attribute holding the revocation epoch the SSO session was created under. */
    public static final String EPOCH_ATTRIBUTE = "cn.nitrowater.account.sessionEpoch";

    private final SessionEpochStore epochs;
    private final Duration absolute;

    /**
     * @param epochs   shared revocation epoch (Redis), read on every session-authenticated request
     * @param absolute {@code app.session.absolute}, hard cap measured from the last password entry
     */
    public AccountSessionGuardFilter(SessionEpochStore epochs, Duration absolute) {
        this.epochs = epochs;
        this.absolute = absolute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SsoUserPrincipal)) {
            // Anonymous, or a bearer JWT: not a browser session, nothing to age out here.
            filterChain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null && !isFresh(session)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @param session current SSO session
     * @return whether the epoch matches and the login is younger than the absolute cap
     */
    private boolean isFresh(HttpSession session) {
        if (!this.epochs.current().equals(session.getAttribute(EPOCH_ATTRIBUTE))) {
            return false;
        }
        return session.getAttribute(PromptLoginFilter.AUTH_AT_ATTRIBUTE) instanceof Instant loginAt
                && loginAt.plus(this.absolute).isAfter(Instant.now());
    }
}
