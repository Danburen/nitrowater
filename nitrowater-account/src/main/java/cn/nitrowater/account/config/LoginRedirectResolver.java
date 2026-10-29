package cn.nitrowater.account.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "where does the browser go after login?".
 *
 * <p>Shared by the hosted form success handler and the JSON code-login endpoint, so both paths
 * resume a pending OIDC authorization, honour a safe explicit {@code redirect}, and fall back to
 * the portal in exactly the same order.</p>
 */
@Component
public class LoginRedirectResolver {

    private final RequestCache requestCache;
    private final String defaultRedirect;

    public LoginRedirectResolver(RequestCache requestCache,
            @Value("${app.sso.default-redirect:http://localhost:5173}") String defaultRedirect) {
        this.requestCache = requestCache;
        this.defaultRedirect = defaultRedirect;
    }

    /**
     * @param request  current request (may carry a saved authorization request in the session)
     * @param response current response (required by the request cache)
     * @return the redirect target, never {@code null}
     */
    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            return savedRequest.getRedirectUrl();
        }

        String explicit = LoginRedirects.safeLocalPath(request.getParameter(LoginRedirects.PARAM));
        if (explicit != null) {
            return explicit;
        }

        return defaultRedirect;
    }
}
