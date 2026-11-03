package cn.nitrowater.server.web;

import cn.nitrowater.server.service.ai.ConversationService;
import cn.nitrowater.server.service.ai.AiSlashService;
import cn.nitrowater.server.web.dto.AiSlashClearReq;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
@RestController
public class AiSlashController {

    private final AiSlashService slash;

    public AiSlashController(AiSlashService slash) {
        this.slash = slash;
    }

    /** Login: today's UTC-day quota snapshot. */
    @GetMapping("/api/ai/slash/quota")
    public AiSlashService.Quota quota(Principal principal) {
        return slash.quota(CurrentUser.requireUid(principal));
    }

    /** Login: the stored assistant preferences (defaults when no row exists). */
    @GetMapping("/api/ai/slash/profile")
    public AiSlashService.Profile profile(Principal principal) {
        return slash.profile(CurrentUser.requireUid(principal));
    }

    /** Login: runtime self-check for troubleshooting. */
    @GetMapping("/api/ai/slash/status")
    public AiSlashService.Status status() {
        return slash.status();
    }

    /**
     * Login: forget the stored turns of one conversation without deleting them.
     *
     * @param request conversation to reset
     * @param principal authenticated user
     * @return how many messages left the context
     */
    @PostMapping("/api/ai/slash/clear")
    public ConversationService.ContextReset clear(@Valid @RequestBody AiSlashClearReq request,
            Principal principal) {
        return slash.clearContext(CurrentUser.requireUid(principal), request.conversationUuid());
    }
}
