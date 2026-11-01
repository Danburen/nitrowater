package cn.nitrowater.server.web;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.properties.AiProperties;
import cn.nitrowater.server.ai.AiQuotaExceededException;
import cn.nitrowater.server.ai.ChatTurn;
import cn.nitrowater.server.ai.agent.AgentAnswer;
import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.agent.AiAgentService;
import cn.nitrowater.server.ai.agent.PreparedAgent;
import cn.nitrowater.server.ai.conversation.ConversationService;
import cn.nitrowater.server.ai.moderation.AiModerationService;
import cn.nitrowater.server.ai.skill.SkillRegistry;
import cn.nitrowater.server.web.dto.AiAgentReq;
import cn.nitrowater.server.web.dto.AiAgentResp;
import cn.nitrowater.server.web.dto.AiSkillResp;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent endpoints.
 *
 * <ul>
 *   <li>{@code GET  /api/ai/skills} — available skills for the picker;</li>
 *   <li>{@code POST /api/ai/agent/chat} — one agent turn with tools;</li>
 *   <li>{@code POST /api/ai/agent/chat/stream} — SSE: {@code conversation} → {@code start} →
 *       {@code delta}* → {@code sources} → {@code tools} → {@code skills} → {@code done}.</li>
 * </ul>
 *
 * <p>The tool loop is Spring AI's auto-registered {@code ToolCallingAdvisor}; this controller only
 * handles transport, conversation persistence and the {@link AgentTrace} at completion.</p>
 */
@RestController
public class AiAgentController {

    private static final long STREAM_TIMEOUT_MS = 180_000L;

    /** Synthetic prompt for "continue generating"; it is never persisted as a user message. */
    private static final String CONTINUE_INSTRUCTION =
            "继续上一次被截断的回答：直接从截断处接着写，不要重复已经输出的内容，也不要重新开头。";

    private final AiAgentService agentService;
    private final ConversationService conversations;
    private final SkillRegistry skills;
    private final AiProperties properties;
    private final AiAdminSettingsService adminSettings;
    private final AiModerationService moderation;
    private final String chatModel;
    private final boolean interruptOnDisconnect;

    public AiAgentController(AiAgentService agentService, ConversationService conversations,
            SkillRegistry skills, AiProperties properties, AiAdminSettingsService adminSettings,
            AiModerationService moderation,
            @Value("${spring.ai.openai.chat.model:}") String chatModel,
            @Value("${app.ai.agent.interrupt-on-disconnect:false}") boolean interruptOnDisconnect) {
        this.agentService = agentService;
        this.conversations = conversations;
        this.skills = skills;
        this.properties = properties;
        this.adminSettings = adminSettings;
        this.moderation = moderation;
        this.chatModel = chatModel;
        this.interruptOnDisconnect = interruptOnDisconnect;
    }

    /** Login: list skills for the chat picker. */
    @GetMapping("/api/ai/skills")
    public List<AiSkillResp> listSkills() {
        if (!adminSettings.get().skillsEnabled()) {
            return List.of();
        }
        return skills.list().stream()
                .map(skill -> new AiSkillResp(skill.name(), skill.description(), skill.whenToUse(), skill.source()))
                .toList();
    }

    /** Login: one complete agent turn (no streaming). */
    @PostMapping("/api/ai/agent/chat")
    public AiAgentResp chat(@Valid @RequestBody AiAgentReq request, Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        ConversationService.StartedTurn turn = conversations.start(uid, request.conversationUuid(),
                request.question());
        List<ChatTurn> history = historyOf(request, turn);
        try {
            AgentAnswer answer = agentService.answer(
                    request.question(), history, request.skill(), request.allowWeb(), uid,
                    turn.conversation().getUuid());
            AiModerationService.Screened screened = moderation.screenAnswer(uid,
                    turn.conversation().getUuid(), answer.answer());
            String text = screened.text();
            conversations.finish(turn.conversation(), text, answer.sources(), answer.tools(), chatModel,
                    answer.usage(), screened.masked());
            return new AiAgentResp(turn.conversation().getUuid(), text, answer.sources(),
                    answer.tools(), answer.skills(), answer.grounded());
        } catch (RuntimeException ex) {
            conversations.fail(turn.conversation(), message(ex));
            throw ex;
        }
    }

    /**
     * Login: stream one agent turn.
     *
     * <p>Tool calls run before the final answer tokens; the trace is therefore complete when the
     * flux completes, and the citations/tool trace are persisted and sent just before
     * {@code done}. The {@code conversation} event carries the uuid so a client that started a new
     * conversation can keep using it.</p>
     */
    @PostMapping(value = "/api/ai/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AiAgentReq request, Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        boolean continuing = request.continueGeneration();
        // A continuation reuses the stored transcript (which already holds the truncated answer)
        // and must not append another user message.
        ConversationService.StartedTurn turn = continuing
                ? conversations.resume(request.conversationUuid(), uid)
                : conversations.start(uid, request.conversationUuid(), request.question());
        List<ChatTurn> history = historyOf(request, turn);
        String question = continuing ? CONTINUE_INSTRUCTION : request.question();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        send(emitter, "conversation", Map.of("uuid", turn.conversation().getUuid()));
        send(emitter, "start", Map.of("mode", "agent"));

        PreparedAgent prepared;
        try {
            prepared = agentService.prepare(
                    question,
                    history,
                    request.skill(),
                    request.allowWeb(),
                    uid,
                    turn.conversation().getUuid()
            );
        } catch (RuntimeException ex) {
            conversations.fail(turn.conversation(), message(ex));
            send(emitter, "error", errorPayload(ex));
            emitter.complete();
            return emitter;
        }

        AtomicBoolean clientGone = new AtomicBoolean(false);
        AtomicBoolean tracesSent = new AtomicBoolean();
        AtomicReference<String> finishReason = new AtomicReference<>();
        StringBuilder answer = new StringBuilder();

        // The subscription is deliberately decoupled from the emitter lifecycle: when the client
        // disconnects, clientGone stops further writes but the model keeps running (unless
        // app.ai.agent.interrupt-on-disconnect=true), so the full answer is still stored.
        Mono<Void> pipeline = prepared.chunks()
                .doOnNext(chunk -> {
                    if (chunk.step() != null) {
                        if (!clientGone.get() && !send(emitter, "step", chunk.step())) {
                            clientGone.set(true);
                        }
                        return;
                    }
                    if (chunk.finishReason() != null) {
                        finishReason.set(chunk.finishReason());
                    }
                    String token = chunk.text();
                    if (token.isEmpty()) {
                        return;
                    }
                    // Accumulate before checking clientGone: tokens produced after a disconnect
                    // still belong to the stored answer.
                    answer.append(token);
                    if (clientGone.get()) {
                        return;
                    }
                    // The prompt makes the model announce its action before calling a tool, so the
                    // first visible token can arrive before any tool ran. Wait until there is a
                    // trace to report, otherwise the once-only guard would ship an empty tool list
                    // and drop the real one.
                    if (!prepared.trace().calls().isEmpty() && tracesSent.compareAndSet(false, true)) {
                        sendTraces(emitter, prepared.trace());
                    }
                    if (!send(emitter, "delta", Map.of("text", token))) {
                        clientGone.set(true);
                    }
                })
                .then(Mono.fromRunnable(() -> {  // heavy persistence running on
                    AgentTrace trace = prepared.trace();
                    String raw = answer.toString();
                    AiModerationService.Screened screened = moderation.screenAnswer(uid,
                            turn.conversation().getUuid(), raw);
                    String text = screened.text();
                    conversations.finish(turn.conversation(), text,
                            trace.sources(), trace.calls(), chatModel,
                            prepared.usageSession().finish(), screened.masked());
                    if (clientGone.get()) return;
                    // Already-streamed tokens cannot be recalled, so the client replaces the bubble
                    // with the screened text that was just stored.
                    if (!text.equals(raw)) {
                        send(emitter, "moderation", Map.of("text", text));
                    }
                    if (tracesSent.compareAndSet(false, true)) sendTraces(emitter, trace);
                    send(emitter, "done", donePayload(finishReason.get()));
                    emitter.complete();
                }).subscribeOn(Schedulers.boundedElastic()))
                .doOnError(error -> Mono.fromRunnable(() -> {
                    conversations.fail(turn.conversation(), message(error));
                    if (clientGone.get()) return;
                    send(emitter, "error", errorPayload(error));
                    emitter.complete();
                }).subscribeOn(Schedulers.boundedElastic()).subscribe())
                .doFinally(signal -> prepared.usageSession().finish())
                .then();

        Disposable disposable = pipeline.subscribe(
                null,
                _ -> {
                });

        emitter.onCompletion(() -> {
            if (interruptOnDisconnect) {
                disposable.dispose();
            }
        });
        emitter.onError(error -> {
            if (interruptOnDisconnect) {
                disposable.dispose();
            }
        });
        emitter.onTimeout(() -> {
            // The client will never see the rest: continue persisting without writing, then
            // release the async request. Without this flag doOnComplete would still try to send.
            clientGone.set(true);
            if (interruptOnDisconnect) {
                disposable.dispose();
            }
            emitter.complete();
        });
        return emitter;
    }

    /** Emits the per-turn citations, tool trace and skills (idempotent once per stream). */
    private static void sendTraces(SseEmitter emitter, AgentTrace trace) {
        send(emitter, "sources", Map.of(
                "sources", trace.sources(),
                "grounded", trace.hasSources()));
        send(emitter, "tools", Map.of("tools", trace.calls()));
        send(emitter, "skills", Map.of("skills", trace.skills()));
    }

    /**
     * @param finishReason provider stop reason, or {@code null} when the provider omitted it
     * @return {@code {finishReason, truncated}}; {@code truncated} is true on a token limit stop
     */
    private static Map<String, Object> donePayload(String finishReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("finishReason", finishReason);
        payload.put("truncated", "length".equalsIgnoreCase(finishReason));
        return payload;
    }

    /**
     * Server-side history is authoritative when continuing an existing conversation; the
     * client-supplied list is only used for legacy callers that start without a uuid.
     */
    private List<ChatTurn> historyOf(AiAgentReq request, ConversationService.StartedTurn turn) {
        if (request.conversationUuid() != null && !request.conversationUuid().isBlank()) {
            return turn.history();
        }
        return AiHistory.toTurns(request.history(), properties.maxHistoryTurns());
    }

    /**
     * Writes one SSE frame, serialising the payload as a JSON string.
     *
     * <p>The payload is kept as an {@code Object} so every call site can pass a
     * {@code Map<String, Object>} directly (and the {@code step} record without conversion), while
     * the SSE frame still carries the JSON text the browser expects. Passing a pre-serialised
     * {@code String} would double-encode it.</p>
     *
     * @return whether the frame was written; a broken connection is ignored
     */
    private static boolean send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(JsonUtil.toJson(payload)));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Builds the {@code error} event payload. Quota exhaustion also carries a stable {@code code},
     * so the client can switch to its exhausted state without parsing the message.
     *
     * @param error the failure
     * @return payload with {@code message} and, for a quota rejection, {@code code}
     */
    private static Map<String, Object> errorPayload(Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message(error));
        if (error instanceof AiQuotaExceededException quota) {
            payload.put("code", quota.code());
        }
        return payload;
    }

    private static String message(Throwable error) {
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
    }
}
