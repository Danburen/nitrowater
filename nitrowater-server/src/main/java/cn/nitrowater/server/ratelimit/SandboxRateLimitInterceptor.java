package cn.nitrowater.server.ratelimit;

import cn.nitrowater.server.sandbox.SandboxProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Enforces rate limits on the sandbox run endpoint.
 *
 * <p>Two gates are applied before the handler runs: a per-client-IP sliding window and a global
 * concurrency gate. The client IP is taken from {@code X-Forwarded-For} (populated by the BFF);
 * a global slot acquired here is released in {@link #afterCompletion}.</p>
 */
@Component
public class SandboxRateLimitInterceptor implements HandlerInterceptor {

    private static final String SLOT_KEY = "nitrowater:sandbox:inflight";
    private static final String RATE_KEY_PREFIX = "nitrowater:sandbox:rate:";
    private static final String SLOT_ATTRIBUTE = "nitrowater.sandbox.slot";

    private final RedisRateLimiter limiter;
    private final SandboxProperties properties;

    public SandboxRateLimitInterceptor(RedisRateLimiter limiter, SandboxProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!limiter.allow(RATE_KEY_PREFIX + clientIp(request), properties.perIpPerMinute())) {
            reject(response, "rate_limited", "Too many requests, please slow down");
            return false;
        }
        if (!limiter.acquire(SLOT_KEY, properties.globalConcurrency())) {
            reject(response, "busy", "Sandbox is busy, please retry shortly");
            return false;
        }
        request.setAttribute(SLOT_ATTRIBUTE, Boolean.TRUE);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(SLOT_ATTRIBUTE))) {
            limiter.release(SLOT_KEY);
        }
    }

    /** The originating client IP, honouring the single-trust-hop {@code X-Forwarded-For} set by the BFF. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Writes a 429 JSON body and stops the chain. */
    private static void reject(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
