package cn.nitrowater.server.service.ai;

import cn.nitrowater.core.web.ApiResponseWriter;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.infra.ratelimit.RedisRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.security.Principal;
import java.util.Set;
import cn.nitrowater.lib.utils.StringUtil;

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
    private final AiUsageService usage;

    public AiRateLimitInterceptor(RedisRateLimiter limiter, AiProperties properties,
            AiAdminSettingsService settings, AiUsageService usage) {
        this.limiter = limiter;
        this.properties = properties;
        this.settings = settings;
        this.usage = usage;
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
        // Platform-wide budget: fail fast and with a distinguishable code so the web client can
        // explain WHY chat is closed (history and slash commands keep working - they never reach
        // a provider). Slash requests do not pass through this interceptor at all.
        AiUsageService.GlobalUsage global = usage.globalToday();
        if (global.tripped()) {
            rejectGlobal(response, global);
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
        if (principal == null || StringUtil.isBlank(principal.getName())) {
            return null;
        }
        return principal.getName();
    }

    /** Rejects a model call because the all-users daily ceiling is exhausted. */
    private static void rejectGlobal(HttpServletResponse response, AiUsageService.GlobalUsage global)
            throws IOException {
        ApiResponseWriter.error(response, HttpStatus.TOO_MANY_REQUESTS.value(), "global_quota_exceeded",
                "全站当日 AI token 额度已用完，请明天再试。",
                Map.of("limit", global.dailyTokenLimit(), "used", global.totalTokens()));
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        ApiResponseWriter.error(response, status.value(), code, message);
    }
}
