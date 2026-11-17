package cn.nitrowater.account.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * The OIDC authorization request that sent the browser to the login page, if any.
 *
 * <p>Both "which app am I logging in to" and the legal links rendered with it
 * ({@code /api/auth/login-context}) need the pending {@code client_id}, so the lookup lives here
 * instead of in each controller.</p>
 */
@Component
public class PendingAuthorizationRequest {

    /**
     * Attribute the exception translation filter writes into the session. Used as a fallback
     * because the {@link RequestCache} is configured with a matcher that only admits HTML
     * navigations, while these endpoints are called with a JSON {@code Accept} header.
     */
    private static final String SAVED_REQUEST_ATTR = "SPRING_SECURITY_SAVED_REQUEST";

    /** The authorization endpoint whose query carries {@code client_id}. */
    private static final String AUTHORIZE_PATH_SUFFIX = "/oauth2/authorize";

    private final RequestCache requestCache;

    public PendingAuthorizationRequest(RequestCache requestCache) {
        this.requestCache = requestCache;
    }

    /**
     * @param request  current request (holds the session with the saved request)
     * @param response current response, required by {@link RequestCache#getRequest}
     * @return the pending client id, or {@code null} when this page was opened directly
     */
    public String clientId(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = this.requestCache.getRequest(request, response);
        if (saved == null) {
            HttpSession session = request.getSession(false);
            Object attribute = session == null ? null : session.getAttribute(SAVED_REQUEST_ATTR);
            saved = attribute instanceof SavedRequest pending ? pending : null;
        }
        return clientIdOf(saved);
    }

    /**
     * Extracts {@code client_id} from a saved authorization request.
     *
     * @param saved pending request, may be {@code null}
     * @return the client id, or {@code null} when this is not an OIDC authorization request
     */
    private static String clientIdOf(SavedRequest saved) {
        if (saved == null) {
            return null;
        }
        String url = saved.getRedirectUrl();
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || !path.endsWith(AUTHORIZE_PATH_SUFFIX)) {
                return null;
            }
            String query = uri.getRawQuery();
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                if (separator > 0 && "client_id".equals(pair.substring(0, separator))) {
                    String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                    return value.isBlank() ? null : value;
                }
            }
        } catch (IllegalArgumentException e) {
            // A malformed saved URL means "unknown client"; the page must still render.
        }
        return null;
    }
}
