package cn.nitrowater.account.config;

import cn.nitrowater.account.security.AccountSessionGuardFilter;
import cn.nitrowater.account.security.PromptLoginFilter;
import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Post-login redirect for the SSO hosted form.
 *
 * <p>The default handler sends the browser to {@code /}, which this service used to forward to
 * {@code /admin/}; that made every normal main-site login land in the admin console. The order is
 * now:</p>
 *
 * <ol>
 *   <li>Spring's saved request: an OIDC {@code /oauth2/authorize} (or an anonymous {@code /admin/}
 *       hit) is resumed with its {@code continue} marker, exactly as before.</li>
 *   <li>An explicit, same-origin {@code redirect} form field — used by the admin console when a
 *       background API call returns 401 after the SPA has already loaded.</li>
 *   <li>The configured portal fallback. Never {@code /admin/}.</li>
 * </ol>
 */
@Component
public class SsoLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final LoginRedirectResolver redirectResolver;
    private final SessionEpochStore epochs;

    /**
     * Creates the handler.
     *
     * @param redirectResolver shared resolver (saved request → safe explicit redirect → portal)
     * @param epochs           shared revocation epoch stamped on the new session
     */
    public SsoLoginSuccessHandler(LoginRedirectResolver redirectResolver, SessionEpochStore epochs) {
        this.redirectResolver = redirectResolver;
        this.epochs = epochs;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        clearAuthenticationAttributes(request);
        HttpSession session = request.getSession();
        // OIDC `auth_time`: PromptLoginFilter / AccountSessionGuardFilter use it to enforce
        // app.session.absolute, because SAS does not implement max_age/auth_time itself.
        session.setAttribute(PromptLoginFilter.AUTH_AT_ATTRIBUTE, Instant.now());
        // A later "revoke all sessions" rotates this value and both services reject the session.
        session.setAttribute(AccountSessionGuardFilter.EPOCH_ATTRIBUTE, this.epochs.current());
        String target = redirectResolver.resolve(request, response);
        // If the saved request is an authorization URL carrying prompt=login/select_account, let
        // PromptLoginFilter know this is the one resumed pass that must not bounce back to /login.
        PromptLoginFilter.markIfNeeded(session, target);
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
