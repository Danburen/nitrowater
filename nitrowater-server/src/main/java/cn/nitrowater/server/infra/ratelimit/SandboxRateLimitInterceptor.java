package cn.nitrowater.server.infra.ratelimit;

import cn.nitrowater.core.web.ApiResponseWriter;

import cn.nitrowater.server.service.sandbox.SandboxExecutionGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.security.Principal;

/**
 * Enforces quotas and admission control on the sandbox run endpoint.
 *
 * <p>Admission is delegated to {@link SandboxExecutionGuard} so the HTTP endpoint and the agent's
 * {@code runCode} tool share the same per-minute/day quotas and the same global queue. The acquired
 * lease is released in {@link #afterCompletion}.</p>
 *
 * <p>The endpoint is login-only, so quotas are keyed by user rather than IP; the BFF terminates
 * the browser connection and would otherwise make every caller share one address.</p>
 */
@Component
public class SandboxRateLimitInterceptor implements HandlerInterceptor {

    private static final String LEASE_ATTRIBUTE = "nitrowater.sandbox.lease";

    private final SandboxExecutionGuard guard;

    public SandboxRateLimitInterceptor(SandboxExecutionGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uid = userId(request);
        if (uid == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Login is required to run code");
            return false;
        }

        try {
            request.setAttribute(LEASE_ATTRIBUTE, guard.admit(uid));
            return true;
        } catch (SandboxExecutionGuard.GuardException ex) {
            reject(response, ex.status(), ex.code(), ex.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (request.getAttribute(LEASE_ATTRIBUTE) instanceof SandboxGate.Lease lease) {
            lease.close();
        }
    }

    /** The authenticated user id, taken from the JWT {@code sub} exposed as the principal name. */
    private static String userId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return null;
        }
        return principal.getName();
    }

    /** Writes a JSON error body and stops the chain. */
    private static void reject(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        ApiResponseWriter.error(response, status.value(), code, message);
    }
}
