package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.AccountSessionService;
import cn.nitrowater.bff.web.SessionCookies;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Session introspection for the SPA. The browser never sees a token; it asks the BFF who it is.
 */
@RestController
@RequestMapping("/bff")
public class SessionController {

    private final AccountSessionService accounts;

    public SessionController(AccountSessionService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        if (user == null) {
            // The browser may still hold a cookie for a session the server no longer has (Redis
            // restart, expired session, newer deployment). Drop it so the dead id stops being sent,
            // and tell the SPA this was a lost session rather than a first visit.
            boolean staleCookie = hasSessionCookie(request);
            response.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.expired().toString());
            Map<String, Object> anonymous = new LinkedHashMap<>();
            anonymous.put("authenticated", false);
            if (staleCookie) {
                anonymous.put("expired", true);
            }
            return anonymous;
        }
        // Backfill sessions that were created before multi-account support was deployed.
        accounts.ensureSlot(request, authentication);
        Map<String, Object> claims = user.getClaims();
        Set<String> roles = new LinkedHashSet<>();
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof List<?> list) {
            for (Object role : list) {
                if (role != null) {
                    roles.add(String.valueOf(role));
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("uid", asString(claims.get("uid")));
        body.put("username", asString(claims.get("preferred_username")));
        body.put("name", asString(claims.get("name")));
        body.put("picture", asString(claims.get("picture")));
        body.put("roles", new ArrayList<>(roles));
        body.put("did", asString(claims.get("did")));
        body.put("issuer", asString(claims.get("iss")));
        // exp is not always present in the userinfo-backed claims; fall back to the ID token.
        Long expiresAt = null;
        Object exp = claims.get("exp");
        if (exp instanceof Number number) {
            expiresAt = number.longValue();
        } else if (user.getIdToken() != null && user.getIdToken().getExpiresAt() != null) {
            expiresAt = user.getIdToken().getExpiresAt().getEpochSecond();
        }
        body.put("expiresAt", expiresAt);
        body.put("accounts", accounts.views(request));
        body.put("activeUid", accounts.activeUid(request));
        return body;
    }

    /**
     * @param request current request
     * @return whether the browser sent a non-empty BFF session cookie
     */
    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (SessionCookies.NAME.equals(cookie.getName())
                    && StringUtil.isNotBlank(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
