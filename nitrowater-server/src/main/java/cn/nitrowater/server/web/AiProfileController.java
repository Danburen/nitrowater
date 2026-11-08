package cn.nitrowater.server.web;

import cn.nitrowater.server.service.ai.AiProfileService;
import cn.nitrowater.server.web.dto.AiProfileUpdateReq;
import cn.nitrowater.server.web.vo.AiProfileResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * NitroBot profile endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/ai/profile} — stored preferences (defaults when no row exists);</li>
 *   <li>{@code PUT /api/ai/profile} — partial update with an audit row per changed field.</li>
 * </ul>
 */
@RestController
public class AiProfileController {

    private final AiProfileService profiles;

    public AiProfileController(AiProfileService profiles) {
        this.profiles = profiles;
    }

    /** Login: read the caller's profile. */
    @GetMapping("/api/ai/profile")
    public AiProfileResp get(Principal principal) {
        return AiProfileResp.from(profiles.get(CurrentUser.requireUid(principal)));
    }

    /** Login: update the caller's profile. */
    @PutMapping("/api/ai/profile")
    public AiProfileResp update(@Valid @RequestBody AiProfileUpdateReq request,
            Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        return AiProfileResp.from(profiles.update(uid, request.toUpdate()));
    }
}
