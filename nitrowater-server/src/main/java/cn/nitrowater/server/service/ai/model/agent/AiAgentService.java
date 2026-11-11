package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.agent.*;
import cn.nitrowater.server.service.ai.ops.AiAdminSettingsService;
import cn.nitrowater.server.infra.exception.AiTurnTimeoutException;
import cn.nitrowater.server.config.ai.AiProperties;
import cn.nitrowater.server.service.ai.model.usage.AiTokenUsageMeter;
import cn.nitrowater.server.service.ai.ops.AiUsageService;
import cn.nitrowater.server.service.ai.model.usage.AiUsageTracker;
import cn.nitrowater.server.service.ai.memory.AiMemoryService;
import cn.nitrowater.server.service.ai.memory.AiProfileService;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.memory.MemoryView;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.skill.AgentSkillService;
import cn.nitrowater.server.service.ai.tool.AgentToolRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NitroBot's general agent.
 *
 * <p>The assistant talks to {@link ChatModel} directly and runs the tool loop itself instead of
 * using Spring AI's {@code ChatClient} + {@code ToolCallingAdvisor}. That advisor aggregates the
 * whole model stream before emitting anything whenever tool options are present
 * ({@code OpenAiChatOptions} always implements {@code ToolCallingChatOptions}), so every answer
 * would reach the browser as one single SSE delta. Streaming the raw model flux keeps tokens
 * incremental while tool calls are still detected from the aggregated round.</p>
 *
 * <p>One turn owns exactly one {@link AgentRun}. Answer tokens, round boundaries and tool
 * invocations all publish into it, so the transport forwards one ordered stream and the browser can
 * render process (steps) and answer (text) as separate pieces of UI.</p>
 *
 * <p>Tool call limits are still enforced by the auto-configured {@link ToolCallingManager}
 * ({@code spring.ai.tools.limits.*}); {@link AgentTrace} is carried through the tool context and is
 * complete once the stream finishes.</p>
 */
@Service
public class AiAgentService {

    private static final ChatClientMessageAggregator AGGREGATOR = new ChatClientMessageAggregator();
    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /**
     * Injected as a user message when the daily budget is nearly spent.
     *
     * <p>It replaces the tool call the model was about to make instead of aborting the turn: the
     * answer the user already paid for still arrives, and the model says what it could not verify.</p>
     */
    private static final String QUOTA_WRAP_UP = """
            [系统提示] 今日 AI token 额度即将用完，本轮不要再调用任何工具。请直接基于已获得的信息给出最终回答，并用一句话说明还有哪些内容未能核实。
            """;

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final AgentToolRegistry toolRegistry;
    private final AgentSkillService skillService;
    private final AiProperties properties;
    private final PromptBuilder promptBuilder;
    private final AiAdminSettingsService settings;
    private final AiUsageService usageService;
    private final AiProfileService profileService;
    private final AiMemoryService memoryService;
    private final int maxRounds;
    private final long turnTimeoutMs;
    /**
     * Provider retries of a round that failed before a single token reached the client.
     *
     * <p>A stream cannot be retried once it has emitted something: the browser already rendered
     * those tokens and a replay would duplicate them. Up to that point the attempt is invisible, so
     * a transient 503/429 is worth one more try instead of turning it into a failed turn. {@code 0}
     * disables it.</p>
     */
    private final int retryAttempts;
    private final long retryBackoffMs;
    /** Below this much remaining daily budget no new tool batch starts; the turn wraps up instead. */
    private final long quotaFloorTokens;
    /** Zone of the per-turn clock block handed to the model, so "today" is never inferred. */
    private final ZoneId turnZone;

    public AiAgentService(ChatModel chatModel, ToolCallingManager toolCallingManager,
                          AgentToolRegistry toolRegistry, AgentSkillService skillService,
                          AiProperties properties, PromptBuilder promptBuilder, AiAdminSettingsService settings,
                          AiUsageService usageService, AiProfileService profileService, AiMemoryService memoryService,
                          @Value("${app.ai.agent.max-rounds:18}") int maxRounds,
                          @Value("${app.ai.agent.turn-timeout-ms:150000}") long turnTimeoutMs,
                          @Value("${app.ai.agent.retry-attempts:1}") int retryAttempts,
                          @Value("${app.ai.agent.retry-backoff-ms:400}") long retryBackoffMs,
                          @Value("${app.ai.agent.quota-floor-tokens:10000}") long quotaFloorTokens,
                          @Value("${app.ai.agent.time-zone:Asia/Shanghai}") String timeZone) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolRegistry = toolRegistry;
        this.skillService = skillService;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.settings = settings;
        this.usageService = usageService;
        this.profileService = profileService;
        this.memoryService = memoryService;
        this.maxRounds = Math.max(1, maxRounds);
        this.turnTimeoutMs = Math.max(1L, turnTimeoutMs);
        this.retryAttempts = Math.max(0, retryAttempts);
        this.retryBackoffMs = Math.max(1L, retryBackoffMs);
        this.quotaFloorTokens = Math.max(0L, quotaFloorTokens);
        this.turnZone = ZoneId.of(timeZone);
    }

    /**
     * Runs one complete agent turn (no streaming).
     *
     * <p>The turn budget is checked between rounds only: a synchronous provider call cannot be
     * interrupted, so this path can overshoot by at most one model call plus one tool batch. The
     * streaming path bounds each round itself.</p>
     *
     * @param question user question
     * @param history previous turns
     * @param skillNames skills the user selected for this turn (empty = automatic)
     * @param allowWeb whether the user allowed web search for this turn
     * @param uid authenticated user id whose daily token quota is charged
     * @param conversationUuid conversation that owns this turn, used as the memory source
     * @return answer + citations + tool trace
     */
    public AgentAnswer answer(String question, List<ChatTurn> history, List<String> skillNames,
            boolean allowWeb, long uid, String conversationUuid) {
        AiSettings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        List<Skill> selected = skillService.resolveSelected(skillNames, runtime);
        AgentTrace trace = createTraceWithSkill(selected, String.valueOf(uid), conversationUuid);
        // No run: this path has no live channel, so the tools publish no steps — but they still get
        // their per-call deadline.
        AgentRequest request = request(sanitized, history, selected, allowWeb, trace, runtime, uid, null);
        AiUsageTracker session = usageService.start(uid, request.estimatedInputTokens());
        long deadline = deadlineNanos();
        try {
            Prompt prompt = request.prompt();
            requireTurnBudget(deadline);
            ChatResponse response = chatModel.call(prompt);
            int round = 0;
            while (hasToolCalls(response) && hasToolCallbacks(prompt.getOptions())) {
                session.record(usageOf(response));
                // Commit the round before the next one starts: a long tool turn must not stay
                // invisible to other turns until it settles.
                session.flush();
                if (round >= maxRounds) {
                    prompt = new Prompt(prompt.getInstructions(), withoutTools(prompt.getOptions()));
                } else if (outOfBudget(session)) {
                    // Wrap up with what the tools already returned instead of spending what is left
                    // on a batch whose answer nobody could afford to read.
                    log.info("Daily token budget nearly spent: ending the turn without further tools");
                    prompt = new Prompt(withWrapUp(prompt.getInstructions()),
                            withoutTools(prompt.getOptions()));
                } else {
                    // Checked before the batch: a tool call started with no budget left would only
                    // overshoot the whole-turn limit by its own deadline.
                    requireTurnBudget(deadline);
                    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
                    if (result.returnDirect()) {
                        break;
                    }
                    prompt = new Prompt(result.conversationHistory(), prompt.getOptions());
                }
                round++;
                requireTurnBudget(deadline);
                response = chatModel.call(prompt);
            }
            session.record(usageOf(response));
            String answer = textOf(response).trim();
            session.append(answer);
            return new AgentAnswer(answer, trace.sources(), trace.calls(), trace.skills(),
                    trace.hasSources(), session.finish());
        } catch (RuntimeException ex) {
            session.finish();
            throw ex;
        }
    }

    /**
     * Starts one agent turn and returns the answer event stream.
     *
     * <p>Tool rounds run before the tokens of the next round; the trace is complete when the flux
     * completes. The first event is always a {@code thinking} step, so the browser can open its
     * progress timeline before the model produces anything.</p>
     *
     * @param question user question
     * @param history previous turns
     * @param skillNames skills the user selected for this turn (empty = automatic)
     * @param allowWeb whether the user allowed web search for this turn
     * @param uid authenticated user id whose daily token quota is charged
     * @param conversationUuid conversation that owns this turn, used as the memory source
     * @return trace + event stream
     */
    public PreparedAgent prepare(
            String question,
            List<ChatTurn> history,
            List<String> skillNames,
            boolean allowWeb,
            long uid,
            String conversationUuid
    ) {
        AiSettings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        List<Skill> selected = skillService.resolveSelected(skillNames, runtime);

        AgentTrace trace = createTraceWithSkill(selected, String.valueOf(uid), conversationUuid);
        AgentRun run = new AgentRun();
        AgentRequest request = request(sanitized, history, selected, allowWeb, trace, runtime, uid, run);
        AiUsageTracker session = usageService.start(uid, request.estimatedInputTokens());
        long deadline = deadlineNanos();
        Flux<AiStreamChunk> chunks = run.events()
                .doOnSubscribe(ignored -> {
                    run.step(AiStreamChunk.Step.thinking());
                    driveRound(request.prompt(), 0, deadline, run, session);
                })
                .doOnNext(chunk -> {
                    session.record(chunk.usage());
                    session.append(chunk.text());
                })
                .doFinally(ignored -> session.finish());
        return new PreparedAgent(trace, chunks, session);
    }

    /**
     * Streams one round and, when the model asked for tools, executes them and drives the next
     * round. Visible deltas leave immediately; only tool-call detection waits for the round to
     * finish.
     *
     * @param prompt prompt of this round
     * @param round  zero-based round index
     * @param deadline {@link System#nanoTime()} value at which the turn must stop
     * @param run    turn stream that receives tokens and steps
     * @param session quota accounting for this turn, used to stop before the budget runs out
     */
    private void driveRound(Prompt prompt, int round, long deadline, AgentRun run,
            AiUsageTracker session) {
        if (run.cancelled()) {
            // The consumer went away between rounds: the next call would only bill tokens nobody
            // can read. The sink is already terminated, so there is nothing to complete.
            return;
        }
        long remaining = remainingNanos(deadline);
        if (remaining <= 0) {
            run.fail(turnTimeout());
            return;
        }
        // Forward each raw chunk as it arrives; the aggregator is only used to inspect the round
        // once it completes. Note it emits a *running* aggregate per chunk (not one final element),
        // so the decision is taken in onComplete with the last snapshot. The timeout is the turn
        // budget that is left, so a stalled provider cannot hold the stream open.
        AtomicBoolean emitted = new AtomicBoolean();
        // defer(), not a bare stream() call: a retry has to issue a new provider request, and the
        // per-round timeout has to be recomputed from what is left of the turn instead of reusing
        // the budget measured before the failed attempt. Resubscribing the same Flux would replay
        // whatever it already produced (or the same failure) instead.
        Flux<ChatResponse> raw = Flux.defer(() -> {
                    long left = remainingNanos(deadline);
                    if (left <= 0) {
                        return Flux.error(turnTimeout());
                    }
                    return chatModel.stream(prompt).timeout(Duration.ofNanos(left));
                })
                .onErrorMap(TimeoutException.class, error -> turnTimeout())
                .doOnNext(response -> {
                    AiStreamChunk chunk = AiStreamChunk.from(response);
                    if (!chunk.text().isEmpty() || chunk.usage() != null
                            || chunk.finishReason() != null) {
                        emitted.set(true);
                    }
                    emit(run, chunk);
                })
                .retryWhen(retryBeforeFirstToken(emitted));
        AtomicReference<ChatResponse> last = new AtomicReference<>();
        Flux<ChatClientResponse> aggregated = AGGREGATOR.aggregateChatClientResponse(
                raw.map(response -> new ChatClientResponse(response, Map.of())),
                ignored -> {
                });
        run.track(aggregated.subscribe(
                one -> last.set(one.chatResponse()),
                run::fail,
                () -> completeRound(last.get(), prompt, round, deadline, run, session)));
    }

    /**
     * Retries a round that failed before anything reached the client.
     *
     * <p>Emitting is one-way: once a delta is out, a replay would duplicate it, so the filter stops
     * retrying from the first visible chunk on. A turn timeout is excluded as well — the budget is
     * already gone, and another attempt would only overshoot it.</p>
     *
     * @param emitted whether this round has already published a chunk
     * @return retry policy; a no-op when {@code app.ai.agent.retry-attempts} is {@code 0}
     */
    private Retry retryBeforeFirstToken(AtomicBoolean emitted) {
        return Retry.backoff(Math.max(1, retryAttempts), Duration.ofMillis(retryBackoffMs))
                .filter(error -> retryAttempts > 0
                        && !emitted.get()
                        && !(error instanceof AiTurnTimeoutException))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    /**
     * Decides whether the round ended the turn or asked for tools.
     *
     * <p>Tool markers are published by the instrumented callbacks ({@link StepTrackingToolCallback}) one invocation at a time, so this method only has to bracket the
     * rounds: next-round {@code thinking}, budget exhaustion, or completion.</p>
     *
     * @param response aggregated round payload
     * @param prompt   prompt of the round that just finished
     * @param round    zero-based round index
     * @param deadline {@link System#nanoTime()} value at which the turn must stop
     * @param run      turn stream
     * @param session  quota accounting for this turn
     */
    private void completeRound(ChatResponse response, Prompt prompt, int round, long deadline,
            AgentRun run, AiUsageTracker session) {
        if (run.cancelled()) {
            // Cancelled while the round was in flight: its usage is already recorded by the stream
            // consumer, and no further round should start.
            return;
        }
        // Publish this round's spend before deciding anything else, so a concurrent turn of the same
        // user is checked against a day that already includes it.
        session.flush();
        if (!hasToolCalls(response) || !hasToolCallbacks(prompt.getOptions())) {
            run.complete();
            return;
        }
        try {
            if (round >= maxRounds) {
                // Budget exhausted: one final round without tools so the turn still terminates.
                run.step(AiStreamChunk.Step.thinking());
                driveRound(new Prompt(prompt.getInstructions(), withoutTools(prompt.getOptions())),
                        round + 1, deadline, run, session);
                return;
            }
            if (outOfBudget(session)) {
                // Same shape as the round cap, but the reason is money: answer from what the tools
                // already returned and let the model name what it could not verify.
                log.info("Daily token budget nearly spent: wrapping up without executing tools");
                run.step(AiStreamChunk.Step.thinking());
                driveRound(new Prompt(withWrapUp(prompt.getInstructions()),
                                withoutTools(prompt.getOptions())),
                        round + 1, deadline, run, session);
                return;
            }
            // The batch is executed synchronously, so the turn budget is checked here: starting one
            // more batch with no time left would only overshoot by that batch's own deadlines.
            requireTurnBudget(deadline);
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            if (result.returnDirect()) {
                run.complete();
                return;
            }
            if (run.cancelled()) {
                // The batch finished after the client left; the next round would bill tokens into a
                // stream nobody is reading.
                return;
            }
            run.step(AiStreamChunk.Step.thinking());
            driveRound(new Prompt(result.conversationHistory(), prompt.getOptions()), round + 1,
                    deadline, run, session);
        } catch (RuntimeException ex) {
            run.fail(ex);
        }
    }

    /**
     * @param session quota accounting of the current turn
     * @return whether the daily budget left is below the floor that still allows another tool batch
     */
    private boolean outOfBudget(AiUsageTracker session) {
        return quotaFloorTokens > 0 && session.remainingTokens() < quotaFloorTokens;
    }

    /**
     * @param instructions messages of the current round
     * @return the same messages plus the wrap-up instruction, so the model answers instead of
     *         asking for another tool
     */
    private static List<Message> withWrapUp(List<Message> instructions) {
        List<Message> messages = new ArrayList<>(instructions.size() + 1);
        messages.addAll(instructions);
        messages.add(new UserMessage(QUOTA_WRAP_UP));
        return messages;
    }

    private static void emit(AgentRun run, AiStreamChunk chunk) {
        // A frame may carry only the stop reason (empty text, no usage): dropping it lost
        // `truncated`, so an answer cut by max-tokens offered no "continue" affordance.
        if (chunk.text().isEmpty() && chunk.usage() == null && chunk.finishReason() == null) {
            return;
        }
        run.emit(chunk);
    }

    /**
     * Builds the prompt and the tool set of one turn.
     * <p>
     * One settings read for the whole turn: the switches come from the snapshot already loaded
     * by requireEnabled(), and the administrator's disable list is fetched once instead of once
     * per tool. `allowWeb` is the user's consent only — whether the provider is configured is
     * the tool's own business, so it must not be folded in here.
     * @param question  sanitized question
     * @param history   previous turns
     * @param selected  skills the user selected, possibly empty
     * @param allowWeb  whether web search may be offered
     * @param trace     turn trace shared with the tools
     * @param runtime   current admin settings
     * @param uid       authenticated user id
     * @param run       turn stream for live tool markers, or {@code null} on the non-streaming path
     * @return prompt plus the input-token estimate used for quota reservation
     */
    private AgentRequest request(String question, List<ChatTurn> history, List<Skill> selected,
            boolean allowWeb, AgentTrace trace, AiSettings runtime, long uid,
            AgentRun run) {
        List<ToolCallback> callbacks = new ArrayList<>();
        List<AgentTool> turnTools = toolRegistry.forTurn(runtime, settings.disabledTools(), allowWeb);
        List<String> toolNames = new ArrayList<>(turnTools.size());
        for (AgentTool tool : turnTools) {
            trace.registerRender(tool.name(), tool.render());
            callbacks.add(track(tool, run, trace));
            toolNames.add(tool.name());
        }
        // The system prompt stays identical across turns so the provider can reuse its cached
        // prefix; everything that depends on this question goes into the user message.
        String context = userContext(uid, question);
        String system = promptBuilder.agentSystem(
                skillService.catalog(runtime), selected, properties.collections(), toolNames);
        String user = promptBuilder.agentUser(question, history, ZonedDateTime.now(turnZone), context);
        List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
        Prompt prompt = new Prompt(messages, chatOptions(callbacks, trace));
        return new AgentRequest(
                prompt,
                AiTokenUsageMeter.estimateTokens(system + user)
        );
    }

    /**
     * @param tool  registered tool
     * @param run   turn stream, or {@code null} when there is no live channel
     * @param trace turn trace
     * @return the callback wrapped so it enforces its deadline and publishes step markers
     */
    private static ToolCallback track(AgentTool tool, AgentRun run, AgentTrace trace) {
        return new StepTrackingToolCallback(tool.callback(), run, trace, tool.timeoutMs(),
                tool.render());
    }

    /**
     * @return the deadline of a turn that starts now
     */
    private long deadlineNanos() {
        return System.nanoTime() + this.turnTimeoutMs * NANOS_PER_MILLI;
    }

    private static long remainingNanos(long deadline) {
        return deadline - System.nanoTime();
    }

    /**
     * @param deadline turn deadline
     * @throws AiTurnTimeoutException when the turn has no budget left
     */
    private void requireTurnBudget(long deadline) {
        if (remainingNanos(deadline) <= 0) {
            throw turnTimeout();
        }
    }

    private AiTurnTimeoutException turnTimeout() {
        return new AiTurnTimeoutException(this.turnTimeoutMs);
    }

    /**
     * Loads the user's profile and question-relevant memories for the user message.
     *
     * <p>Personalization is best-effort: a database failure must not take down chat, so the turn
     * continues without this block and the failure is logged.</p>
     *
     * <p>It is not part of the system prompt because recalled memories change per question: a
     * changing head would turn every request into a prefix-cache miss for the whole history.</p>
     */
    private String userContext(long uid, String question) {
        try {
            String profile = profileService.promptBlock(uid);
            List<MemoryView> recalled = memoryService.recall(uid, question, 3);
            return promptBuilder.agentContext(profile, recalled);
        } catch (RuntimeException ex) {
            log.warn("Failed to assemble user context for uid {}: {}", uid, ex.getMessage());
            return "";
        }
    }

    /**
     * Builds request options from the model's configured defaults so model name, temperature and
     * max tokens keep coming from {@code spring.ai.openai.chat.*}.
     */
    private ChatOptions chatOptions(List<ToolCallback> callbacks, AgentTrace trace) {
        ChatOptions defaults = chatModel.getOptions();
        ToolCallingChatOptions.Builder<?> builder = defaults instanceof ToolCallingChatOptions toolOptions
                ? toolOptions.mutate()
                : ToolCallingChatOptions.builder();
        return builder
                .toolCallbacks(callbacks)
                .toolContext(Map.of(AgentTrace.CONTEXT_KEY, trace))
                .build();
    }

    private static boolean hasToolCallbacks(ChatOptions options) {
        return options instanceof ToolCallingChatOptions toolOptions
                && toolOptions.getToolCallbacks() != null
                && !toolOptions.getToolCallbacks().isEmpty();
    }

    private static ChatOptions withoutTools(ChatOptions options) {
        if (options instanceof ToolCallingChatOptions toolOptions) {
            return toolOptions.mutate().toolCallbacks(List.of()).build();
        }
        return options;
    }

    private static boolean hasToolCalls(ChatResponse response) {
        return response != null && response.hasToolCalls();
    }

    private static org.springframework.ai.chat.metadata.Usage usageOf(ChatResponse response) {
        return response == null || response.getMetadata() == null
                ? null
                : response.getMetadata().getUsage();
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return Objects.requireNonNullElse(response.getResult().getOutput().getText(), "");
    }

    // Selected skills are inlined into the system prompt, so no loadSkill call occurs;
    // record them here so the UI can still show which workflows were applied.
    @NotNull
    private static AgentTrace createTraceWithSkill(List<Skill> skills, String userId,
            String conversationUuid) {
        AgentTrace trace = new AgentTrace(userId, conversationUuid);
        for (Skill skill : skills) {
            trace.addSkill(skill.name());
        }
        return trace;
    }

    private AiSettings requireEnabled() {
        AiSettings runtime = settings.get();
        if (!properties.enabled() || !runtime.aiEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant is disabled");
        }
        if (!runtime.agentEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI agent mode is disabled");
        }
        return runtime;
    }

    private record AgentRequest(Prompt prompt, int estimatedInputTokens) {
    }
}
