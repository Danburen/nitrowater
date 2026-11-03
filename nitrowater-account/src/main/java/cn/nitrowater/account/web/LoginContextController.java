package cn.nitrowater.account.web;

import cn.nitrowater.account.web.dto.response.LoginContextResp;
import cn.nitrowater.lib.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Context for the hosted login page.
 *
 * <p>When an application starts an OIDC login, the browser is bounced through
 * {@code /oauth2/authorize}, Spring caches that navigation as the pending (saved) request in the
 * session, and the page redirects to {@code /login}. This endpoint reads that cached request and
 * reports which client is asking, so the page can say "you are logging in to X" instead of a
 * generic greeting.</p>
 *
 * <p>Only the client id / display name are returned — never the redirect URL, the state, or the
 * PKCE challenge. The client id is already public (it is in the browser's address bar of the
 * authorization request).</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginContextController {

    /**
     * Session attribute used by {@code HttpSessionRequestCache} for the pending request.
     *
     * <p>{@link RequestCache#getRequest} normally returns it already; the direct read is a
     * fallback because that cache is configured with a matcher that only admits HTML
     * navigations, while this endpoint is called with a JSON {@code Accept} header.</p>
     */
    private static final String SAVED_REQUEST_ATTR = "SPRING_SECURITY_SAVED_REQUEST";

    /** The authorization endpoint whose query carries {@code client_id}. */
    private static final String AUTHORIZE_PATH_SUFFIX = "/oauth2/authorize";

    private final RequestCache requestCache;
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * Reports the client of the pending authorization request, if any.
     *
     * @param request  current request (holds the session with the saved request)
     * @param response current response, required by {@link RequestCache#getRequest}
     * @return {@link LoginContextResp} with {@code null} fields when nothing is pending
     */
    @GetMapping("/login-context")
    public ApiResponse<LoginContextResp> loginContext(HttpServletRequest request, HttpServletResponse response) {
        String clientId = clientIdOf(pendingRequest(request, response));
        if (clientId == null) {
            return ApiResponse.success(new LoginContextResp(null, null));
        }
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        String clientName = client != null && StringUtil.isNotBlank(client.getClientName())
                ? client.getClientName()
                : clientId;
        return ApiResponse.success(new LoginContextResp(clientId, clientName));
    }

    /** Reads the pending pre-login request without creating a session. */
    private SavedRequest pendingRequest(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null) {
            return saved;
        }
        HttpSession session = request.getSession(false);
        Object attribute = session == null ? null : session.getAttribute(SAVED_REQUEST_ATTR);
        return attribute instanceof SavedRequest pending ? pending : null;
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
