package cn.nitrowater.bff.security;

import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Records every successful OIDC login in the session's account pool, then redirects to {@code /}.
 *
 * <p>The redirect policy is unchanged from the previous {@code defaultSuccessUrl("/", true)}: the
 * SPA resumes its own in-app destination from {@code sessionStorage}, so the BFF must not try to
 * replay a saved request.</p>
 *
 * <p>The session is stamped with the current revocation epoch (Redis), the instant the credentials
 * were proved, and the fingerprint the SPA handed over during the authorization redirect, so
 * {@link SessionGuardFilter} can enforce all three later.</p>
 */
@Component
public class AccountLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AccountSessionService accounts;
    private final SessionEpochStore epochs;

    public AccountLoginSuccessHandler(AccountSessionService accounts, SessionEpochStore epochs) {
        this.accounts = accounts;
        this.epochs = epochs;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        accounts.registerLogin(request, authentication);
        // Session fixation protection has already rotated the session, so this is the final one.
        HttpSession session = request.getSession();
        session.setAttribute(SessionGuardFilter.EPOCH_ATTRIBUTE, this.epochs.current());
        session.setAttribute(SessionGuardFilter.AUTH_AT_ATTRIBUTE, Instant.now());
        // The fingerprint arrives as a query parameter on the authorization redirect (a top-level
        // navigation cannot carry a header); one login consumes it.
        Object fingerprint = session.getAttribute(SessionGuardFilter.PENDING_DEVICE_ATTRIBUTE);
        session.removeAttribute(SessionGuardFilter.PENDING_DEVICE_ATTRIBUTE);
        String deviceHash = SessionGuardFilter.hashDevice(
                fingerprint instanceof String value ? value : null);
        if (deviceHash == null) {
            session.removeAttribute(SessionGuardFilter.DEVICE_ATTRIBUTE);
        } else {
            session.setAttribute(SessionGuardFilter.DEVICE_ATTRIBUTE, deviceHash);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
