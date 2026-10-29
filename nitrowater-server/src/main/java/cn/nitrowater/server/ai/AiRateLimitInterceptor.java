package cn.nitrowater.server.ai;

import cn.nitrowater.server.infra.ratelimit.RedisRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.security.Principal;
import java.util.Set;

/**
 * Quota gate for the documentation chat endpoints.
 *
 * <p>Model calls cost money, so the chat endpoints are login-only and limited per user by a
 * sliding-minute request cap. Daily limits are enforced in tokens by {@link AiUsageService}. The BFF
 * terminates the browser connection, so quotas are keyed by the JWT {@code sub} (user id) rather
 * than by IP.</p>
 */
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final String QUOTA_KEY_PREFIX = "nitrowater:ai:quota:";
    private static final Set<String> AGENT_PATHS = Set.of(
            "/api/ai/agent/chat", "/api/ai/agent/chat/stream");

    private final RedisRateLimiter limiter;
    private final AiProperties properties;
    private final AiAdminSettingsService settings;

    public AiRateLimitInterceptor(RedisRateLimiter limiter, AiProperties properties,
            AiAdminSettingsService settings) {
        this.limiter = limiter;
        this.properties = properties;
        this.settings = settings;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        AiAdminSettingsService.Settings runtime = settings.get();
        if (!properties.enabled() || !runtime.aiEnabled()) {
            reject(response, HttpStatus.SERVICE_UNAVAILABLE, "ai_disabled", "AI assistant is disabled");
            return false;
        }
        boolean agentRequest = AGENT_PATHS.contains(request.getRequestURI());
        if (agentRequest ? !runtime.agentEnabled() : !runtime.chatEnabled()) {
            reject(response, HttpStatus.SERVICE_UNAVAILABLE, "ai_mode_disabled", "This AI mode is disabled");
            return false;
        }
        String uid = userId(request);
        if (uid == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Login is required to use the assistant");
            return false;
        }
        if (!limiter.allowSliding(QUOTA_KEY_PREFIX + uid + ":m", properties.perUserPerMinute())) {
            reject(response, HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                    "Too many questions, please slow down");
            return false;
        }
        return true;
    }

    /** The authenticated user id, taken from the JWT {@code sub} exposed as the principal name. */
    private static String userId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return null;
        }
        return principal.getName();
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
