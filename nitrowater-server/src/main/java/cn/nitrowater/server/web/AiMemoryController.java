package cn.nitrowater.server.web;

import cn.nitrowater.server.ai.memory.AiMemoryService;
import cn.nitrowater.server.web.dto.AiMemoryResp;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

/**
 * Long-term memory endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/ai/memories} — active memories, newest first;</li>
 *   <li>{@code DELETE /api/ai/memories} — soft-delete every active memory;</li>
 *   <li>{@code DELETE /api/ai/memories/{uuid}} — soft-delete one memory.</li>
 * </ul>
 *
 * <p>Memories are user-owned; another user's uuid is a 404.</p>
 */
@RestController
public class AiMemoryController {

    private final AiMemoryService memories;

    public AiMemoryController(AiMemoryService memories) {
        this.memories = memories;
    }

    /** Login: list active memories. */
    @GetMapping("/api/ai/memories")
    public List<AiMemoryResp> list(@RequestParam(required = false) Integer limit, Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        int size = limit == null ? 50 : limit;
        return memories.list(uid, size).stream().map(AiMemoryResp::from).toList();
    }

    /** Login: soft-delete every active memory and return the affected count. */
    @DeleteMapping("/api/ai/memories")
    public int clear(Principal principal) {
        return memories.clear(CurrentUser.requireUid(principal), Instant.now());
    }

    /** Login: soft-delete one memory. */
    @DeleteMapping("/api/ai/memories/{uuid}")
    public void delete(@PathVariable String uuid, Principal principal) {
        memories.delete(CurrentUser.requireUid(principal), uuid);
    }
}
