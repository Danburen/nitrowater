package cn.nitrowater.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Invalidates authenticated sessions that belong to a previous deployment.
 *
 * <p>Every successful login stores the current release epoch ({@code app.session.epoch}) in the
 * session. When the epoch no longer matches, the session is destroyed and the security context is
 * cleared, so the request falls through as anonymous: {@code /api/**} answers 401 with
 * {@code session_expired} and {@code /bff/me} answers anonymous. Bumping the epoch on deploy is
 * therefore enough to force every browser to sign in again — no Redis flush required.</p>
 *
 * <p>Only authenticated sessions are checked: anonymous sessions (which exist for CSRF tokens)
 * legitimately have no epoch yet.</p>
 */
public class SessionEpochFilter extends OncePerRequestFilter {

    /** Session attribute holding the epoch the session was created under. */
    public static final String EPOCH_ATTRIBUTE = "cn.nitrowater.bff.sessionEpoch";

    /** Request attribute the {@link JsonAuthenticationEntryPoint} turns into an {@code X-Auth-Reason}. */
    public static final String REASON_ATTRIBUTE = "cn.nitrowater.bff.authReason";

    private final String epoch;

    public SessionEpochFilter(String epoch) {
        this.epoch = epoch;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isAuthenticated(authentication)) {
            HttpSession session = request.getSession(false);
            Object current = session == null ? null : session.getAttribute(EPOCH_ATTRIBUTE);
            if (!epoch.equals(current)) {
                invalidate(request);
                request.setAttribute(REASON_ATTRIBUTE, "session_epoch_mismatch");
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Destroys the session and clears the in-memory security context.
     *
     * @param request current request
     */
    public static void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
