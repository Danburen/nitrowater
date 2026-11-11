package cn.nitrowater.server.service.ai.agent;

import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.server.infra.exception.CodedException;
import cn.nitrowater.server.service.ai.conversation.AiCompactService;
import cn.nitrowater.server.service.ai.conversation.ConversationService;
import cn.nitrowater.server.service.ai.model.agent.AgentTurnCommand;
import cn.nitrowater.server.service.ai.model.agent.AgentTurnResult;
import cn.nitrowater.server.service.ai.model.agent.ChatTurn;
import cn.nitrowater.server.service.ai.model.agent.ConversationTurn;
import cn.nitrowater.server.service.ai.model.moderation.ScreenedAnswer;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.model.agent.AgentAnswer;
import cn.nitrowater.server.service.ai.model.agent.AgentEventSink;
import cn.nitrowater.server.service.ai.model.agent.AgentStreamHandle;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.agent.AiAgentService;
import cn.nitrowater.server.service.ai.model.agent.PreparedAgent;
import cn.nitrowater.server.service.ai.ops.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.ops.AiModerationService;
import cn.nitrowater.server.config.ai.AiProperties;
import cn.nitrowater.server.service.ai.skill.AgentSkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestration of one agent turn: from the persisted question to the stored answer.
 *
 * <p>This is the application service behind the three chat endpoints. It owns conversation
 * bookkeeping, moderation, token accounting and the SSE turn protocol; the controller only
 * authenticates the caller, converts the request DTO into an {@link AgentTurnCommand}, opens the
 * response and adapts {@link AgentEventSink} to an {@code SseEmitter}.</p>
 *
 * <ul>
 *   <li>{@link #pickerSkills()} — skills offered by the chat picker;</li>
 *   <li>{@link #chat(long, AgentTurnCommand)} — one complete turn, no streaming;</li>
 *   <li>{@link #stream(long, AgentTurnCommand, AgentEventSink)} — one streamed turn.</li>
 * </ul>
 *
 * <p>Streamed turns keep persisting after a client disconnect: tokens produced without a reader
 * still belong to the stored answer, so writes are skipped but accumulation continues (unless
 * {@code app.ai.agent.interrupt-on-disconnect} cancels the model call).</p>
 */
@Service
public class AgentTurnService {

    private static final Logger log = LoggerFactory.getLogger(AgentTurnService.class);

    /** Synthetic prompt for "continue generating"; it is never persisted as a user message. */
    private static final String CONTINUE_INSTRUCTION =
            "继续上一次被截断的回答：直接从截断处接着写，不要重复已经输出的内容，也不要重新开头。";

    private final AiAgentService agentService;
    private final ConversationService conversationService;
    private final AiModerationService moderation;
    private final AgentSkillService skillService;
    private final AiAdminSettingsService adminSettings;
    private final AiCompactService compactService;
    private final AiProperties properties;
    private final String chatModel;
    private final boolean interruptOnDisconnect;

    public AgentTurnService(
            AiAgentService agentService,
            ConversationService conversationService,
            AiModerationService moderation,
            AgentSkillService skillService,
            AiAdminSettingsService adminSettings,
            AiCompactService compactService,
            AiProperties properties,
            @Value("${spring.ai.openai.chat.model:}") String chatModel,
            @Value("${app.ai.agent.interrupt-on-disconnect:false}") boolean interruptOnDisconnect)
    {
        this.agentService = agentService;
        this.conversationService = conversationService;
        this.moderation = moderation;
        this.skillService = skillService;
        this.adminSettings = adminSettings;
        this.compactService = compactService;
        this.properties = properties;
        this.chatModel = chatModel;
        this.interruptOnDisconnect = interruptOnDisconnect;
    }

    /**
     * Skills exposed by the chat picker.
     *
     * @return an empty list when the administrator switched skills off
     */
    public List<Skill> pickerSkills() {
        return skillService.catalog(adminSettings.get());
    }

    /**
     * Runs one complete turn without streaming.
     *
     * <p>The question is persisted first (so a rejected or failed turn still leaves a transcript
     * row), then the model and tools answer, then the screened answer is stored.</p>
     *
     * @param uid authenticated user id
     * @param command turn command assembled from the request body
     * @return the answer with its citations, tool trace and skills
     */
    public AgentTurnResult chat(long uid, AgentTurnCommand command) {
        ConversationTurn turn = conversationService.start(uid, command.conversationUuid(),
                command.question());
        List<ChatTurn> history = extraChatTurns(command, turn);
        try {
            AgentAnswer answer = agentService.answer(
                    command.question(), history, command.skills(), command.allowWeb(), uid,
                    turn.conversation().getUuid());
            ScreenedAnswer screened = moderation.screenAnswer(uid,
                    turn.conversation().getUuid(), answer.answer());
            String text = screened.text();
            conversationService.finish(
                    turn.conversation(),
                    text,
                    answer.sources(),
                    answer.tools(),
                    answer.skills(),
                    chatModel,
                    answer.usage(),
                    screened.masked(),
                    false
            );
            autoCompact(uid, turn.conversation().getUuid());
            return new AgentTurnResult(
                    turn.conversation().getUuid(),
                    text, answer.sources(),
                    answer.tools(),
                    answer.skills(),
                    answer.grounded()
            );
        } catch (RuntimeException ex) {
            conversationService.fail(turn.conversation(), message(ex));
            throw ex;
        }
    }

    /**
     * Starts one streamed turn and returns before the model is called.
     *
     * <p>The leading frames are written on the caller thread, so Spring MVC flushes them as soon
     * as the async response is initialized. Context assembly, the quota reservation and the model
     * rounds then run on a worker thread; otherwise the browser would see nothing until the first
     * token arrived.</p>
     *
     * @param uid       authenticated user id
     * @param command   {@link AgentTurnCommand} turn command assembled from the request body
     * @param sink      {@link AgentEventSink} transport that receives the turn events
     * @return handle used by the transport to react to a disconnect or timeout
     */
    public AgentStreamHandle stream(long uid, AgentTurnCommand command, AgentEventSink sink) {
        boolean continuing = command.continueGeneration();
        ConversationTurn turn = continuing
                ? conversationService.resume(command.conversationUuid(), uid)
                : conversationService.start(uid, command.conversationUuid(), command.question());
        List<ChatTurn> history = extraChatTurns(command, turn);
        String question = continuing ? CONTINUE_INSTRUCTION : command.question();

        sink.send("conversation", Map.of("uuid", turn.conversation().getUuid()));
        sink.send("start", Map.of("mode", "agent"));
        sink.send("step", AiStreamChunk.Step.thinking());

        AgentStreamHandle handle = new AgentStreamHandle(interruptOnDisconnect);
        Mono.fromRunnable(
                () -> runTurn(handle, sink, turn, question, history, command, uid, continuing))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.error("Agent turn setup failed for conversation {}",
                        turn.conversation().getUuid(), error));
        return handle;
    }

    /**
     * Prepares one turn and pipes its chunks to the sink on a worker thread.
     *
     * @param handle turn control surface (client-gone flag, model subscription)
     * @param sink transport of the turn
     * @param turn conversation plus the history captured before the question
     * @param question sanitized question, or the continuation instruction
     * @param history previous turns fed to the model
     * @param command turn command
     * @param uid authenticated user id
     * @param continuing whether this turn continues a truncated answer in place
     */
    private void runTurn(
            AgentStreamHandle handle,
            AgentEventSink sink,
            ConversationTurn turn,
            String question,
            List<ChatTurn> history,
            AgentTurnCommand command,
            long uid,
            boolean continuing
    ) {
        PreparedAgent prepared;
        try {
            prepared = agentService.prepare(
                    question,
                    history,
                    command.skills(),
                    command.allowWeb(),
                    uid,
                    turn.conversation().getUuid()
            );
        } catch (RuntimeException ex) {
            conversationService.fail(turn.conversation(), message(ex));
            if (!handle.clientGone()) {
                sink.send("error", errorPayload(ex));
                sink.complete();
            }
            return;
        }

        StringBuilder answer = new StringBuilder();
        AtomicReference<String> finishReason = new AtomicReference<>();

        // The subscription is deliberately decoupled from the transport lifecycle: when the client
        // disconnects, clientGone stops further writes but the model keeps running (unless
        // interrupt-on-disconnect is set), so the full answer is still stored.
        Mono<Void> pipeline = prepared.chunks()
                .doOnNext(chunk -> {
                    if (chunk.step() != null) {
                        if (!handle.clientGone() && !sink.send("step", chunk.step())) {
                            handle.markClientGone();
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
                    if (handle.clientGone()) {
                        return;
                    }
                    if (!sink.send("delta", Map.of("text", token))) {
                        handle.markClientGone();
                    }
                })
                .then(Mono.fromRunnable(() -> {  // heavy persistence running on
                    AgentTrace trace = prepared.trace();
                    String raw = answer.toString();
                    ScreenedAnswer screened = moderation.screenAnswer(uid,
                            turn.conversation().getUuid(), raw);
                    String text = screened.text();
                    conversationService.finish(turn.conversation(), text,
                            trace.sources(), trace.calls(), trace.skills(), chatModel,
                            prepared.usageSession().finish(), screened.masked(), continuing);
                    autoCompact(uid, turn.conversation().getUuid());
                    if (handle.clientGone()) {
                        return;
                    }
                    // Already-streamed tokens cannot be recalled, so the client replaces the bubble
                    // with the screened text that was just stored.
                    if (!text.equals(raw)) {
                        sink.send("moderation", Map.of("text", text));
                    }
                    // The live step cards already showed the process; the aggregated trace is sent
                    // once at the end, where it becomes the persisted "process" row of the bubble.
                    sendTraces(sink, trace);
                    sink.send("done", donePayload(finishReason.get()));
                    sink.complete();
                }).subscribeOn(Schedulers.boundedElastic()))
                .doOnError(error -> Mono.fromRunnable(() -> {
                    conversationService.fail(turn.conversation(), message(error));
                    if (handle.clientGone()) {
                        return;
                    }
                    sink.send("error", errorPayload(error));
                    sink.complete();
                }).subscribeOn(Schedulers.boundedElastic()).subscribe())
                .doFinally(signal -> prepared.usageSession().finish())
                .then();

        handle.attach(pipeline.subscribe(
                null,
                ignored -> {
                }));
    }

    /**
     * Fires the end-of-turn compaction on a worker thread.
     *
     * <p>Compaction is an optimisation: it must never delay the answer, so it runs after the turn
     * has been stored and its SSE frames flushed. {@link AiCompactService#autoCompact} already
     * swallows failures; the guard here only covers a rejected dispatch.</p>
     *
     * @param uid authenticated user id
     * @param conversationUuid conversation that just finished
     */
    private void autoCompact(long uid, String conversationUuid) {
        if (StringUtil.isBlank(conversationUuid)) {
            return;
        }
        try {
            Mono.fromRunnable(() -> compactService.autoCompact(uid, conversationUuid))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(null, error -> log.warn(
                            "Compaction dispatch failed for conversation {}", conversationUuid, error));
        } catch (RuntimeException ex) {
            log.warn("Compaction dispatch failed for conversation {}", conversationUuid, ex);
        }
    }

    /** Emits the per-turn citations, tool trace and skills (idempotent once per stream). */
    private static void sendTraces(AgentEventSink sink, AgentTrace trace) {
        sink.send("sources", Map.of(
                "sources", trace.sources(),
                "grounded", trace.hasSources()));
        sink.send("tools", Map.of("tools", trace.calls()));
        sink.send("skills", Map.of("skills", trace.skills()));
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
     *
     * @param command turn command
     * @param turn conversation plus the history captured before the question
     * @return the turns to feed the prompt
     */
    private List<ChatTurn> extraChatTurns(AgentTurnCommand command, ConversationTurn turn) {
        if (StringUtil.isNotBlank(command.conversationUuid())) {
            return turn.history();
        }
        List<ChatTurn> supplied = command.history();
        if (supplied.isEmpty()) {
            return List.of();
        }
        int maxEntries = Math.max(0, properties.maxHistoryTurns()) * 2;
        int from = maxEntries == 0 ? supplied.size() : Math.max(0, supplied.size() - maxEntries);
        List<ChatTurn> history = new ArrayList<>(supplied.size() - from);
        for (int index = from; index < supplied.size(); index++) {
            ChatTurn entry = supplied.get(index);
            if (entry != null && StringUtil.isNotBlank(entry.content())) {
                history.add(entry);
            }
        }
        return List.copyOf(history);
    }

    /**
     * Builds the {@code error} event payload. Any coded rejection also carries its stable
     * {@code code}, so the client can switch state without parsing the message.
     *
     * @param error the failure
     * @return payload with {@code message} and, for a {@link CodedException}, {@code code}
     */
    private static Map<String, Object> errorPayload(Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message(error));
        if (error instanceof CodedException coded) {
            payload.put("code", coded.code());
        }
        return payload;
    }

    private static String message(Throwable error) {
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
    }
}
