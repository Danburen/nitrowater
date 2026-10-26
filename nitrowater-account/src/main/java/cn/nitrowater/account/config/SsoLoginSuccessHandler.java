package cn.nitrowater.account.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
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

    private final RequestCache requestCache;
    private final String defaultRedirect;

    /**
     * Creates the handler.
     *
     * @param requestCache    session request cache shared with the security chains
     * @param defaultRedirect portal URL used when neither a saved request nor a safe redirect exists
     */
    public SsoLoginSuccessHandler(RequestCache requestCache,
            @Value("${app.sso.default-redirect:http://localhost:5173}") String defaultRedirect) {
        this.requestCache = requestCache;
        this.defaultRedirect = defaultRedirect;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        clearAuthenticationAttributes(request);

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            getRedirectStrategy().sendRedirect(request, response, savedRequest.getRedirectUrl());
            return;
        }

        String explicit = LoginRedirects.safeLocalPath(request.getParameter(LoginRedirects.PARAM));
        if (explicit != null) {
            getRedirectStrategy().sendRedirect(request, response, explicit);
            return;
        }

        getRedirectStrategy().sendRedirect(request, response, defaultRedirect);
    }
}
