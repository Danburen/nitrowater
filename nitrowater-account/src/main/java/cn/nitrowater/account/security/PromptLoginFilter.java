package cn.nitrowater.account.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Enforces the OIDC {@code prompt=login} / {@code prompt=select_account} intent.
 *
 * <p>Spring Authorization Server 7.1 only enforces {@code prompt=none}; other prompt values are
 * accepted and then ignored. That is fatal for "add another account": the BFF asks for a fresh
 * credential prompt, but the AS silently reuses the current SSO session and immediately returns
 * an authorization code for the same user.</p>
 *
 * <p>This filter runs before {@code OAuth2AuthorizationEndpointFilter}. When an authenticated
 * browser hits {@code /oauth2/authorize?prompt=login}, it saves the pending authorization request,
 * clears the local SSO authentication and redirects to the hosted login page. After a successful
 * login the redirect resolver returns the saved authorization URL; {@link #markIfNeeded} records a
 * one-shot marker so the resumed request passes through instead of looping.</p>
 */
public class PromptLoginFilter extends OncePerRequestFilter {

    /** One-shot session marker: the next matching authorization request has already re-authenticated. */
    public static final String RESUMED_ATTRIBUTE = PromptLoginFilter.class.getName() + ".RESUMED";

    private static final String AUTHORIZATION_PATH = "/oauth2/authorize";
    private static final String LOGIN_PATH = "/login";

    private final RequestCache requestCache;
    private final SessionRegistry sessionRegistry;

    public PromptLoginFilter(RequestCache requestCache, SessionRegistry sessionRegistry) {
        this.requestCache = requestCache;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isAuthorizationRequest(request) || !forcesLogin(request.getParameter("prompt"))) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(RESUMED_ATTRIBUTE))) {
            session.removeAttribute(RESUMED_ATTRIBUTE);
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            // Not signed in: the normal login entry point handles it (and the saved request is
            // resumed by the success handler, which sets the marker).
            filterChain.doFilter(request, response);
            return;
        }

        requestCache.saveRequest(request, response);
        if (session != null) {
            sessionRegistry.removeSessionInformation(session.getId());
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
        SecurityContextHolder.clearContext();
        response.sendRedirect(request.getContextPath() + LOGIN_PATH + "?switch=1");
    }

    /**
     * @param request servlet request
     * @return whether the request targets the authorization endpoint
     */
    private static boolean isAuthorizationRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return AUTHORIZATION_PATH.equals(uri);
    }

    /**
     * @param prompt raw {@code prompt} query value
     * @return whether it asks for an interactive re-authentication
     */
    public static boolean forcesLogin(String prompt) {
        if (StringUtil.isBlank(prompt)) {
            return false;
        }
        for (String value : prompt.trim().split("\\s+")) {
            if ("login".equals(value) || "select_account".equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses {@code prompt} out of a redirect URL.
     *
     * @param url absolute or relative URL, possibly {@code null}
     * @return whether the URL requests an interactive re-authentication
     */
    public static boolean urlForcesLogin(String url) {
        if (url == null) {
            return false;
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        for (String pair : url.substring(queryStart + 1).split("&")) {
            if (pair.startsWith("prompt=")) {
                return forcesLogin(URLDecoder.decode(pair.substring("prompt=".length()),
                        StandardCharsets.UTF_8));
            }
        }
        return false;
    }

    /**
     * Marks the session so the resumed authorization request is let through exactly once.
     *
     * @param session current session, possibly {@code null}
     * @param redirectUrl the target the success handler is about to redirect to
     */
    public static void markIfNeeded(HttpSession session, String redirectUrl) {
        if (session != null && urlForcesLogin(redirectUrl)) {
            session.setAttribute(RESUMED_ATTRIBUTE, Boolean.TRUE);
        }
    }
}
