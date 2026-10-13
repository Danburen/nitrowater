package cn.nitrowater.account.web.admin;

import cn.nitrowater.account.admin.dto.AdminSessionVO;
import cn.nitrowater.account.security.SsoUserPrincipal;
import cn.nitrowater.lib.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin console bootstrap: tells the SPA who it is talking to and which authorities it holds.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSessionController {

    /** Current admin identity (used by the SPA on load to render the header / guard routes). */
    @GetMapping("/session")
    public ApiResponse<AdminSessionVO> session(Authentication authentication) {
        SsoUserPrincipal principal = AdminPrincipals.require(authentication);
        return ApiResponse.success(new AdminSessionVO(
                principal.getUid(), principal.getLoginName(), principal.getDisplayName(), principal.getRoles()));
    }
}
