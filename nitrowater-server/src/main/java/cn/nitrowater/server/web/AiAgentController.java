package cn.nitrowater.server.web;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.AiProperties;
import cn.nitrowater.server.ai.AiQuotaExceededException;
import cn.nitrowater.server.ai.AiTokenUsage;
import cn.nitrowater.server.ai.AiUsageService;
import cn.nitrowater.server.ai.ChatTurn;
import cn.nitrowater.server.ai.agent.AgentAnswer;
import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.agent.AiAgentService;
import cn.nitrowater.server.ai.agent.PreparedAgent;
import cn.nitrowater.server.ai.conversation.ConversationService;
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
    private final String chatModel;

    public AiAgentController(AiAgentService agentService, ConversationService conversations,
            SkillRegistry skills, AiProperties properties, AiAdminSettingsService adminSettings,
            @Value("${spring.ai.openai.chat.model:}") String chatModel) {
        this.agentService = agentService;
        this.conversations = conversations;
        this.skills = skills;
        this.properties = properties;
        this.adminSettings = adminSettings;
        this.chatModel = chatModel;
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
                    request.question(), history, request.skill(), request.allowWeb(), uid);
            conversations.finish(turn.conversation(), answer.answer(), answer.sources(), answer.tools(), chatModel,
                    answer.usage());
            return new AiAgentResp(turn.conversation().getUuid(), answer.answer(), answer.sources(),
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
        send(emitter, "conversation", JsonUtil.toJson(Map.of("uuid", turn.conversation().getUuid())));
        send(emitter, "start", JsonUtil.toJson(Map.of("mode", "agent")));

        PreparedAgent prepared;
        try {
            prepared = agentService.prepare(
                    question,
                    history,
                    request.skill(),
                    request.allowWeb(),
                    uid
            );
        } catch (RuntimeException ex) {
            conversations.fail(turn.conversation(), message(ex));
            send(emitter, "error", JsonUtil.toJson(errorPayload(ex)));
            emitter.complete();
            return emitter;
        }

        AtomicReference<Disposable> subscription = new AtomicReference<>();
        AtomicReference<String> finishReason = new AtomicReference<>();
        AtomicBoolean tracesSent = new AtomicBoolean();
        StringBuilder answer = new StringBuilder();
        Disposable disposable = prepared.chunks().subscribe(
                chunk -> {
                    if (chunk.step() != null) {
                        send(emitter, "step", JsonUtil.toJson(chunk.step()));
                        return;
                    }
                    if (chunk.finishReason() != null) {
                        finishReason.set(chunk.finishReason());
                    }
                    String token = chunk.text();
                    if (token.isEmpty()) {
                        return;
                    }
                    // Tool rounds are done before the first visible token, so the sources are final
                    // here: emitting them now lets inline [n] chips resolve while the answer streams.
                    if (tracesSent.compareAndSet(false, true)) {
                        sendTraces(emitter, prepared.trace());
                    }
                    answer.append(token);
                    if (!send(emitter, "delta", JsonUtil.toJson(Map.of("text", token)))) {
                        Disposable running = subscription.get();
                        if (running != null) {
                            running.dispose();
                        }
                    }
                },
                error -> {
                    conversations.fail(turn.conversation(), message(error));
                    send(emitter, "error", JsonUtil.toJson(errorPayload(error)));
                    emitter.complete();
                },
                () -> {
                    AgentTrace trace = prepared.trace();
                    AiTokenUsage tokenUsage = prepared.usageSession().finish();
                    conversations.finish(turn.conversation(), answer.toString(), trace.sources(), trace.calls(),
                            chatModel, tokenUsage);
                    if (tracesSent.compareAndSet(false, true)) {
                        sendTraces(emitter, trace);
                    }
                    send(emitter, "done", JsonUtil.toJson(donePayload(finishReason.get())));
                    emitter.complete();
                });
        subscription.set(disposable);
        emitter.onCompletion(disposable::dispose);
        // Settles on client disconnect as well; Session#finish is idempotent.
        emitter.onCompletion(prepared.usageSession()::finish);
        emitter.onError(error -> {
            disposable.dispose();
            prepared.usageSession().finish();
        });
        emitter.onTimeout(() -> {
            disposable.dispose();
            prepared.usageSession().finish();
            emitter.complete();
        });
        return emitter;
    }

    /** Emits the per-turn citations, tool trace and skills (idempotent once per stream). */
    private static void sendTraces(SseEmitter emitter, AgentTrace trace) {
        send(emitter, "sources", JsonUtil.toJson(Map.of(
                "sources", trace.sources(),
                "grounded", trace.hasSources())));
        send(emitter, "tools", JsonUtil.toJson(Map.of("tools", trace.calls())));
        send(emitter, "skills", JsonUtil.toJson(Map.of("skills", trace.skills())));
    }

    /**
     * @param finishReason provider stop reason, or {@code null} when the provider omitted it
     * @return {@code {finishReason, truncated}}; {@code truncated} is true on a token limit stop
     */
    private static Map<String, Object> donePayload(String finishReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("finishReason", finishReason);
        payload.put("truncated", finishReason != null && "length".equalsIgnoreCase(finishReason));
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

    private static boolean send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
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
        if (error instanceof AiQuotaExceededException) {
            payload.put("code", AiQuotaExceededException.CODE);
        }
        return payload;
    }

    private static String message(Throwable error) {
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
    }
}
