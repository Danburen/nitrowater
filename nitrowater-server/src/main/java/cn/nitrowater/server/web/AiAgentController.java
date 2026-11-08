package cn.nitrowater.server.web;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.AgentTurnService;
import cn.nitrowater.server.service.ai.model.agent.AgentEventSink;
import cn.nitrowater.server.service.ai.model.agent.AgentStreamHandle;
import cn.nitrowater.server.web.dto.AiAgentReq;
import cn.nitrowater.server.web.vo.AiAgentResp;
import cn.nitrowater.server.web.vo.AiSkillResp;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.List;

/**
 * Agent endpoints.
 *
 * <ul>
 *   <li>{@code GET  /api/ai/skills} — available skills for the picker;</li>
 *   <li>{@code POST /api/ai/agent/chat} — one agent turn with tools;</li>
 *   <li>{@code POST /api/ai/agent/chat/stream} — SSE: {@code conversation} → {@code start} →
 *       {@code step}* / {@code delta}* (interleaved) → {@code sources} → {@code tools} →
 *       {@code skills} → {@code done}.</li>
 * </ul>
 *
 * <p>This controller is transport only: it authenticates the caller, opens the SSE response and
 * adapts it to {@link AgentEventSink}. Conversation persistence, moderation, token accounting and
 * the turn protocol live in {@link AgentTurnService}.</p>
 */
@RestController
public class AiAgentController {

    /** Upper bound on a streamed turn, after which the turn keeps persisting without writing. */
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final AgentTurnService turns;

    public AiAgentController(AgentTurnService turns) {
        this.turns = turns;
    }

    /** Login: list skills for the chat picker. */
    @GetMapping("/api/ai/skills")
    public List<AiSkillResp> listSkills() {
        return turns.pickerSkills().stream().map(AiSkillResp::fromPicker).toList();
    }

    /** Login: one complete agent turn (no streaming). */
    @PostMapping("/api/ai/agent/chat")
    public AiAgentResp chat(@Valid @RequestBody AiAgentReq request, Principal principal) {
        return AiAgentResp.from(turns.chat(CurrentUser.requireUid(principal), request.toCommand()));
    }

    /**
     * Login: stream one agent turn.
     * The client will never see the rest of a disconnect: keep persisting without writing,
     * then release the async request. onCompletion fires on our own complete() as well, which
     * is harmless because the turn is already stored by then.
     *
     * <p>The handler returns as soon as the leading frames are written, which lets Spring MVC
     * initialize the async response immediately; the turn itself is assembled on a worker thread
     * by {@link AgentTurnService}.</p>
     */
    @PostMapping(value = "/api/ai/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AiAgentReq request, Principal principal) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AgentStreamHandle handle = turns.stream(
                CurrentUser.requireUid(principal),
                request.toCommand(),
                new SseAgentSink(emitter)
        );

        emitter.onCompletion(handle::cancel);
        emitter.onError(error -> handle.cancel());
        emitter.onTimeout(() -> {
            handle.cancel();
            emitter.complete();
        });
        return emitter;
    }

    /**
     * SSE adapter of {@link AgentEventSink}: serialises payloads to JSON frames and treats a broken
     * connection as "client gone" instead of failing the turn.
     *
     * <p>The payload is kept as an {@code Object} so the service can pass a {@code Map} directly
     * while the frame still carries the JSON text the browser expects; passing a pre-serialised
     * {@code String} would double-encode it.</p>
     */
    private record SseAgentSink(SseEmitter emitter) implements AgentEventSink {

        @Override
        public boolean send(String event, Object payload) {
            try {
                emitter.send(SseEmitter.event().name(event).data(JsonUtil.toJson(payload)));
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public void complete() {
            emitter.complete();
        }
    }
}
