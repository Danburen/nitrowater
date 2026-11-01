package cn.nitrowater.server.web;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.AiChatService;
import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.AiDailyUsage;
import cn.nitrowater.server.ai.AiTokenUsage;
import cn.nitrowater.server.ai.properties.AiProperties;
import cn.nitrowater.server.ai.ChatAnswer;
import cn.nitrowater.server.ai.ChatTurn;
import cn.nitrowater.server.ai.DocsIndexService;
import cn.nitrowater.server.ai.PreparedChat;
import cn.nitrowater.server.ai.conversation.ConversationService;
import cn.nitrowater.server.ai.moderation.AiModerationService;
import cn.nitrowater.server.ai.tool.WebSearchProvider;
import cn.nitrowater.server.ai.AiUsageService;
import cn.nitrowater.server.ai.AiUsageTracker;
import cn.nitrowater.server.ai.AiQuotaExceededException;
import cn.nitrowater.server.web.dto.AiChatReq;
import cn.nitrowater.server.web.dto.AiChatResp;
import cn.nitrowater.server.web.dto.AiStatusResp;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Documentation-assistant endpoints.
 *
 * <ul>
 *   <li>{@code GET  /api/ai/status} — index/model status (login required);</li>
 *   <li>{@code POST /api/ai/chat} — one-shot answer with citations;</li>
 *   <li>{@code POST /api/ai/chat/stream} — SSE stream: {@code conversation} → {@code sources} →
 *       {@code delta}* → {@code done} (or {@code error}).</li>
 * </ul>
 *
 * <p>Both chat endpoints are login-only, quota-limited (see {@code AiRateLimitInterceptor}) and
 * persist the turn to the user's conversation; the status endpoint is informational and
 * unlimited.</p>
 */
@RestController
public class AiController {

    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final AiChatService chatService;
    private final ConversationService conversations;
    private final DocsIndexService indexService;
    private final AiProperties properties;
    private final WebSearchProvider webSearchProvider;
    private final AiAdminSettingsService adminSettings;
    private final AiUsageService usage;
    private final AiModerationService moderation;
    private final String chatModel;
    private final String embeddingModel;

    public AiController(AiChatService chatService, ConversationService conversations,
            DocsIndexService indexService, AiProperties properties,
            WebSearchProvider webSearchProvider, AiAdminSettingsService adminSettings, AiUsageService usage,
            AiModerationService moderation,
            @Value("${spring.ai.openai.chat.model:}") String chatModel,
            @Value("${spring.ai.openai.embedding.model:}") String embeddingModel) {
        this.chatService = chatService;
        this.conversations = conversations;
        this.indexService = indexService;
        this.properties = properties;
        this.webSearchProvider = webSearchProvider;
        this.adminSettings = adminSettings;
        this.usage = usage;
        this.moderation = moderation;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    /** Login: report whether the corpus is indexed and which models are configured. */
    @GetMapping("/api/ai/status")
    public AiStatusResp status() {
        DocsIndexService.IndexStatus index = indexService.status();
        AiAdminSettingsService.Settings runtime = adminSettings.get();
        return new AiStatusResp(
                properties.enabled() && runtime.aiEnabled(),
                indexService.indexing(),
                index.indexed(),
                index.files(),
                index.chunks(),
                index.indexedAt(),
                index.durationMs(),
                index.error(),
                indexService.collectionName(),
                chatModel,
                embeddingModel,
                properties.topK(),
                properties.similarityThreshold(),
                runtime.webSearchEnabled() && webSearchProvider.enabled(),
                runtime.dailyTokenLimit(), runtime.chatEnabled(), runtime.agentEnabled(),
                runtime.documentSearchEnabled(), runtime.codeRunnerEnabled(), runtime.skillsEnabled(),
                AiStatusResp.GlobalQuota.from(usage.globalToday()));
    }

    /** Login: return the current user's UTC-day token usage. */
    @GetMapping("/api/ai/usage")
    public AiDailyUsage currentUsage(Principal principal) {
        return usage.forUser(CurrentUser.requireUid(principal));
    }

    /** Login: answer a question in one request. */
    @PostMapping("/api/ai/chat")
    public AiChatResp chat(@Valid @RequestBody AiChatReq request, Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        ConversationService.StartedTurn turn = conversations.start(uid, request.conversationUuid(),
                request.question());
        try {
            ChatAnswer answer = chatService.answer(request.question(), historyOf(request, turn), uid);
            // The screened text is used for both the response and the stored turn, so the database
            // can never hold content the browser was not allowed to see.
            AiModerationService.Screened screened = moderation.screenAnswer(uid,
                    turn.conversation().getUuid(), answer.answer());
            String text = screened.text();
            conversations.finish(turn.conversation(), text, answer.sources(), List.of(), chatModel,
                    answer.usage(), screened.masked());
            return new AiChatResp(turn.conversation().getUuid(), text, answer.sources(),
                    answer.grounded());
        } catch (RuntimeException ex) {
            conversations.fail(turn.conversation(), message(ex));
            throw ex;
        }
    }

    /**
     * Login: stream an answer as Server-Sent Events.
     *
     * <p>Sources are sent before the first token so the UI can render the citation list while the
     * answer is still being generated. Token payloads are JSON strings, which keeps the SSE framing
     * valid even when a token contains a newline. The answer is persisted before {@code done}.</p>
     */
    @PostMapping(value = "/api/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AiChatReq request, Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        ConversationService.StartedTurn turn = conversations.start(uid, request.conversationUuid(),
                request.question());

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        send(emitter, "conversation", JsonUtil.toJson(Map.of("uuid", turn.conversation().getUuid())));

        PreparedChat prepared;
        try {
            prepared = chatService.prepare(request.question(), historyOf(request, turn), uid);
        } catch (RuntimeException ex) {
            conversations.fail(turn.conversation(), message(ex));
            send(emitter, "error", JsonUtil.toJson(errorPayload(ex)));
            emitter.complete();
            return emitter;
        }

        send(emitter, "sources", JsonUtil.toJson(Map.of(
                "sources", prepared.sources(),
                "grounded", prepared.grounded())));

        AtomicReference<Disposable> subscription = new AtomicReference<>();
        StringBuilder answer = new StringBuilder();
        Disposable disposable = prepared.chunks().subscribe(
                chunk -> {
                    String token = chunk.text();
                    answer.append(token);
                    // Usage-only chunks (the final metadata frame) carry no visible text.
                    if (token.isEmpty()) {
                        return;
                    }
                    // A failed send means the browser is gone; stop paying for the rest of the answer.
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
                    AiTokenUsage tokenUsage = prepared.usageSession()
                            .map(AiUsageTracker::finish)
                            .orElseGet(() -> new AiTokenUsage(0, 0, false));
                    String raw = answer.toString();
                    AiModerationService.Screened screened = moderation.screenAnswer(uid,
                            turn.conversation().getUuid(), raw);
                    String text = screened.text();
                    conversations.finish(turn.conversation(), text, prepared.sources(), List.of(),
                            chatModel, tokenUsage, screened.masked());
                    // Streamed tokens cannot be un-sent: when the gate masked something, the client
                    // replaces the bubble with the stored version before it renders "done".
                    if (!text.equals(raw)) {
                        send(emitter, "moderation", JsonUtil.toJson(Map.of("text", text)));
                    }
                    send(emitter, "done", "{}");
                    emitter.complete();
                });
        subscription.set(disposable);
        emitter.onCompletion(disposable::dispose);
        // Also settles when the client disconnects mid-answer: the reservation must never leak, and
        // Session#finish is idempotent with the onComplete branch above.
        emitter.onCompletion(() -> prepared.usageSession().ifPresent(AiUsageTracker::finish));
        emitter.onError(error -> {
            disposable.dispose();
            prepared.usageSession().ifPresent(AiUsageTracker::finish);
        });
        emitter.onTimeout(() -> {
            disposable.dispose();
            prepared.usageSession().ifPresent(AiUsageTracker::finish);
            emitter.complete();
        });
        return emitter;
    }

    /**
     * Server-side history is authoritative when continuing an existing conversation; the
     * client-supplied list is only used for legacy callers that start without a uuid.
     */
    private List<ChatTurn> historyOf(AiChatReq request, ConversationService.StartedTurn turn) {
        if (request.conversationUuid() != null && !request.conversationUuid().isBlank()) {
            return turn.history();
        }
        return AiHistory.toTurns(request.history(), properties.maxHistoryTurns());
    }

    /**
     * Writes one SSE event; a broken connection is ignored (the client is gone).
     *
     * @return whether the event was written
     */
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
        if (error instanceof AiQuotaExceededException quota) {
            payload.put("code", quota.code());
        }
        return payload;
    }

    private static String message(Throwable error) {
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
    }
}
