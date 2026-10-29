package cn.nitrowater.account.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.nitrowater.account.security.PromptLoginFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

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

    /**
     * Creates the handler.
     *
     * @param redirectResolver shared resolver (saved request → safe explicit redirect → portal)
     */
    public SsoLoginSuccessHandler(LoginRedirectResolver redirectResolver) {
        this.redirectResolver = redirectResolver;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        clearAuthenticationAttributes(request);
        String target = redirectResolver.resolve(request, response);
        // If the saved request is an authorization URL carrying prompt=login/select_account, let
        // PromptLoginFilter know this is the one resumed pass that must not bounce back to /login.
        PromptLoginFilter.markIfNeeded(request.getSession(false), target);
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
