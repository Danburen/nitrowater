package cn.nitrowater.account.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import cn.nitrowater.account.security.SsoUserPrincipal;
import cn.nitrowater.core.infrastructure.utils.context.AuthContext;
import cn.nitrowater.core.infrastructure.utils.context.UserCtxHolder;

import java.io.IOException;
import java.util.Locale;

/**
 * Mirrors the identity Spring Security already established into {@link UserCtxHolder}, the
 * ThreadLocal the legacy account services still read.
 *
 * <p>Identity comes only from the {@code SecurityContext} ({@link SsoUserPrincipal}) — never from a header or a bearer token.
 * The client address is {@link HttpServletRequest#getRemoteAddr()}; behind a proxy enable Boot's
 * forwarded-header support instead of trusting a header.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAuthContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            AuthContext context = fromSecurityContext(request);
            if (context != null) {
                UserCtxHolder.set(context);
            }
            if (isProtected(request) && UserCtxHolder.get() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"unauthorized\",\"data\":null}");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            UserCtxHolder.remove();
        }
    }

    /** Identity from the session / OIDC principal established by the security filter chain. */
    private static AuthContext fromSecurityContext(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SsoUserPrincipal principal)) {
            return null;
        }
        return new AuthContext(principal.getUid(), null, principal.getDid(),
                resolveLocale(request), clientIp(request));
    }

    /** Paths that require an identity; without one they fail fast instead of NPE-ing later. */
    private static boolean isProtected(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/auth/account");
    }

    private static Locale resolveLocale(HttpServletRequest request) {
        Locale locale = request.getLocale();
        return locale != null ? locale : Locale.ENGLISH;
    }

    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
