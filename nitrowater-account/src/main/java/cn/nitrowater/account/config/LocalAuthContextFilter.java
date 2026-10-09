package cn.nitrowater.account.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import cn.nitrowater.core.infrastructure.security.RsaJwtUtil;
import cn.nitrowater.core.infrastructure.utils.context.AuthContext;
import cn.nitrowater.core.infrastructure.utils.context.UserCtxHolder;

import java.io.IOException;
import java.util.Locale;

/**
 * Local user info injection
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAuthContextFilter extends OncePerRequestFilter {

    private static final String HEADER_UID = "X-User-Uid";
    private static final String BEARER = "Bearer ";

    private final RsaJwtUtil rsaJwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String uidHeader = request.getHeader(HEADER_UID);
            if (uidHeader != null && !uidHeader.isBlank()) {
                UserCtxHolder.set(new AuthContext(Long.valueOf(uidHeader), null, null,
                        resolveLocale(request), clientIp(request)));
            } else {
                String at = extractBearer(request);
                if (at != null) {
                    try {
                        Claims claims = rsaJwtUtil.parseToken(at);
                        UserCtxHolder.set(new AuthContext(
                                Long.valueOf(claims.getSubject()),
                                claims.getId(),
                                claims.get("did", String.class),
                                resolveLocale(request),
                                clientIp(request)));
                    } catch (Exception e) {
                        log.debug("LocalAuthContextFilter: access token invalid: {}", e.getMessage());
                    }
                }
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

    /** 需要登录的路径：无身份直接 401（替代 Spring Security 的鉴权链）。 */
    private static boolean isProtected(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/auth/account") || uri.startsWith("/api/auth/logout");
    }

    private static String extractBearer(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(BEARER)) {
            return auth.substring(BEARER.length()).trim();
        }
        return null;
    }

    private static Locale resolveLocale(HttpServletRequest request) {
        Locale l = request.getLocale();
        return l != null ? l : Locale.ENGLISH;
    }

    private static String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-Client-Ip");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
