package cn.nitrowater.server.web;

import cn.nitrowater.server.service.ai.chat.AiSlashService;
import cn.nitrowater.server.service.ai.model.agent.ContextReset;
import cn.nitrowater.server.web.dto.AiSlashClearReq;
import cn.nitrowater.server.web.vo.AiContextResetResp;
import cn.nitrowater.server.web.vo.AiProfileResp;
import cn.nitrowater.server.web.vo.AiQuotaResp;
import cn.nitrowater.server.web.vo.AiSlashStatusResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Deterministic endpoints behind the chat slash commands ({@code /quota}, {@code /profile},
 * {@code /status}, {@code /clear}).
 *
 * <ul>
 *   <li>{@code GET  /api/ai/slash/quota} — today's token budget;</li>
 *   <li>{@code GET  /api/ai/slash/profile} — stored preferences;</li>
 *   <li>{@code GET  /api/ai/slash/status} — runtime self-check (models, index, switches, tools);</li>
 *   <li>{@code POST /api/ai/slash/clear} — drop the conversation from the model context.</li>
 * </ul>
 *
 * <p>These are reads and one context reset: no model is called, so they consume zero tokens and
 * never appear in {@code ai_message}. They are also outside the chat rate-limit interceptor, which
 * only guards the four chat paths.</p>
 */
@RequestMapping("/api/ai/slash")
@RestController
public class AiSlashController {

    private final AiSlashService slashService;

    public AiSlashController(AiSlashService slashService) {
        this.slashService = slashService;
    }

    /** Login: today's UTC-day quota snapshot. */
    @GetMapping("/quota")
    public AiQuotaResp quota(Principal principal) {
        return AiQuotaResp.from(
                slashService.quota(CurrentUser.requireUid(principal))
        );
    }

    /** Login: the stored assistant preferences (defaults when no row exists). */
    @GetMapping("/profile")
    public AiProfileResp profile(Principal principal) {
        return AiProfileResp.from(
                slashService.profile(CurrentUser.requireUid(principal))
        );
    }

    /** Login: runtime self-check for troubleshooting. */
    @GetMapping("/status")
    public AiSlashStatusResp status() {
        return AiSlashStatusResp.from(
                slashService.status()
        );
    }

    /**
     * Login: forget the stored turns of one conversation without deleting them.
     *
     * @param request conversation to reset
     * @param principal authenticated user
     * @return how many messages left the context
     */
    @PostMapping("/clear")
    public AiContextResetResp clear(@Valid @RequestBody AiSlashClearReq request,
            Principal principal) {
        ContextReset reset = slashService.clearContext(CurrentUser.requireUid(principal),
                request.conversationUuid());
        return new AiContextResetResp(reset.cleared(), reset.droppedMessages(), reset.boundarySeq());
    }
}
