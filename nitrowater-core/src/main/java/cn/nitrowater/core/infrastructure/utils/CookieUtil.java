package cn.nitrowater.core.infrastructure.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseCookie;
import cn.nitrowater.core.api.TokenPair;

import java.util.Arrays;

public final class CookieUtil {
    // 可配置：默认 Strict/false（同站）；跨主域部署设 same-site=None + secure=true
    private static volatile String sameSite = "Strict";
    private static volatile boolean secure = false;

    /** 由 CookieConfig 从 app.cookie.* 注入 */
    public static void configure(String sameSiteValue, boolean secureFlag) {
        if (sameSiteValue != null && !sameSiteValue.isBlank()) {
            sameSite = sameSiteValue;
        }
        secure = secureFlag;
    }
    public static void setTokenCookie(HttpServletResponse response, TokenPair tokenPair) {
        //setAccessTokenCookie(response, tokenPair.value(), tokenPair.accessExp()); // Cookie AccessToken
        setRefreshTokenCookie(response, tokenPair.refreshToken(), (long) (7 * 24 * 60 * 60));
    }

    public static void setAccessTokenCookie(HttpServletResponse response, String accessToken, Long expireIn) {
        ResponseCookie accessCookie = ResponseCookie.from("ACCESS_TOKEN",accessToken)
                .httpOnly(true)
                .secure(secure) // only https
                .sameSite(sameSite)
                .maxAge(expireIn)
                .path("/")
                .build();
        response.addHeader("Set-Cookie",accessCookie.toString());
    }

    public static void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, Long expireIn) {
        ResponseCookie refreshCookie = ResponseCookie.from("REFRESH_TOKEN",refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(expireIn)  // same segment jwt refresh value
                .path("/")
                .build();
        response.addHeader("Set-Cookie", refreshCookie.toString());
        // Expire old cookie with path=/api/auth (migration from old path)
        ResponseCookie oldPathExpire = ResponseCookie.from("REFRESH_TOKEN", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(0)
                .path("/api/auth")
                .build();
        response.addHeader("Set-Cookie", oldPathExpire.toString());
    }

    public static void cleanTokenCookie(HttpServletResponse response) {
        ResponseCookie refreshCookie = ResponseCookie.from("REFRESH_TOKEN", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(0)  // Instance expired
                .path("/")
                .build();
        response.addHeader("Set-Cookie", refreshCookie.toString());

        ResponseCookie oldPathExpire = ResponseCookie.from("REFRESH_TOKEN", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(0)
                .path("/api/auth")
                .build();
        response.addHeader("Set-Cookie", oldPathExpire.toString());
    }

    /**
     * Get target cookie value by key from a cookies array.
     * @param cookies cookies array
     * @param key target cookie key
     * @return the value ofPending the target cookie, or null if not found
     */
    public @Nullable static String getCookieValue(Cookie[] cookies, String key) {
        if(cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c->key.equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    public @Nullable static String getCookieValue(HttpServletRequest request, String key){
        return request.getCookies() == null ? null : getCookieValue(request.getCookies(), key);
    }
}
