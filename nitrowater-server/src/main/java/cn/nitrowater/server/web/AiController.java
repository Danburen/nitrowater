package cn.nitrowater.server.web;

import cn.nitrowater.server.service.ai.ops.AiStatusService;
import cn.nitrowater.server.service.ai.ops.AiUsageService;
import cn.nitrowater.server.web.vo.AiStatusResp;
import cn.nitrowater.server.web.vo.AiUsageResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * NitroBot informational endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/ai/status} — index/model status and runtime switches (login required);</li>
 *   <li>{@code GET /api/ai/usage} — the caller's UTC-day token usage.</li>
 * </ul>
 *
 * <p>Chat turns only go through {@link AiAgentController}: there is no retrieval-only route, so a
 * question the model answers without calling any tool is simply a single-round agent turn.</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiStatusService statusService;
    private final AiUsageService usage;

    public AiController(AiStatusService statusService, AiUsageService usage) {
        this.statusService = statusService;
        this.usage = usage;
    }

    /** Login: report whether the corpus is indexed and which models are configured. */
    @GetMapping("/status")
    public AiStatusResp status() {
        return AiStatusResp.from(
                statusService.status()
        );
    }

    /** Login: return the current user's UTC-day token usage. */
    @GetMapping("/usage")
    public AiUsageResp currentUsage(Principal principal) {
        return AiUsageResp.from(
                usage.forUser(
                        CurrentUser.requireUid(principal)
                )
        );
    }
}
