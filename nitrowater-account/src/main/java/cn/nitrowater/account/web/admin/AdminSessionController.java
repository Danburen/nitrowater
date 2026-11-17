package cn.nitrowater.account.web.admin;

import cn.nitrowater.account.admin.SessionRevocationService;
import cn.nitrowater.account.admin.dto.AdminSessionVO;
import cn.nitrowater.account.admin.dto.RevokedSessionsVO;
import cn.nitrowater.account.security.PromptLoginFilter;
import cn.nitrowater.account.security.SsoUserPrincipal;
import cn.nitrowater.lib.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * Admin console bootstrap and the global session switch.
 *
 * <p>{@code /api/admin/**} is restricted to {@code ROLE_SUPER_ADMIN} by the servlet security
 * chain, so both endpoints inherit the same guard.</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSessionController {

    private final SessionRevocationService sessionRevocationService;

    /** Step-up window: the operator must have proved a password recently (sudo mode). */
    private static final Duration REAUTH_WINDOW = Duration.ofMinutes(10);

    /** Current admin identity (used by the SPA on load to render the header / guard routes). */
    @GetMapping("/session")
    public ApiResponse<AdminSessionVO> session(Authentication authentication) {
        SsoUserPrincipal principal = AdminPrincipals.require(authentication);
        return ApiResponse.success(new AdminSessionVO(
                principal.getUid(), principal.getLoginName(), principal.getDisplayName(), principal.getRoles()));
    }

    /**
     * Emergency "sign everybody out": rotates the shared epoch, deletes every OAuth2 authorization
     * and every SSO session. The calling admin is logged out too — the console redirects to the
     * login page after acknowledging the result.
     *
     * <p>Because the action is global and destructive, it also requires a recent password entry
     * (10 minutes). A stolen admin session without the password cannot use it to sign out the whole
     * site; the SPA turns {@code reauth_required} into a logout + re-login.</p>
     */
    @Operation(summary = "注销全部登录会话（门户 + SSO + OAuth2 授权）")
    @PostMapping("/session/revoke-all")
    public ResponseEntity<ApiResponse<RevokedSessionsVO>> revokeAll(Authentication authentication,
            HttpServletRequest request) {
        SsoUserPrincipal principal = AdminPrincipals.require(authentication);
        HttpSession session = request.getSession(false);
        Object authAt = (session == null) ? null : session.getAttribute(PromptLoginFilter.AUTH_AT_ATTRIBUTE);
        if (!(authAt instanceof Instant at) || at.plus(REAUTH_WINDOW).isBefore(Instant.now())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<RevokedSessionsVO>reject("reauth_required",
                            "该操作要求最近 10 分钟内验证过密码，请重新登录后再试", null));
        }
        RevokedSessionsVO result = sessionRevocationService.revokeAll(
                principal.getUid(), principal.getLoginName(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        // Spring Session re-saves the current session when the request ends, which would resurrect
        // exactly the one session the service just deleted. Invalidate the wrapper so the commit
        // sees no session and expires the cookie instead.
        session.invalidate();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
