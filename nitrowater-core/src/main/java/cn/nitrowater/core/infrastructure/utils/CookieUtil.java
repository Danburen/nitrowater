package cn.nitrowater.core.infrastructure.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Cookie reading helpers.
 *
 * <p>Only reading lives here now: the legacy access/refresh token cookies are gone with the
 * Phase-1 token shim, and the session cookie's attributes are configured through
 * {@code server.servlet.session.cookie.*}.</p>
 */
public final class CookieUtil {

    private CookieUtil() {
    }

    /**
     * Get target cookie value by key from a cookies array.
     *
     * @param cookies cookies array
     * @param key     target cookie key
     * @return the value of the target cookie, or {@code null} if not found
     */
    public @Nullable static String getCookieValue(Cookie[] cookies, String key) {
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> key.equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    public @Nullable static String getCookieValue(HttpServletRequest request, String key) {
        return request.getCookies() == null ? null : getCookieValue(request.getCookies(), key);
    }
}
