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

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
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
    private final int maxToolRounds;
    private final long turnTimeoutMs;
    /** Zone of the per-turn clock block handed to the model, so "today" is never inferred. */
    private final ZoneId turnZone;

    public AiAgentService(ChatModel chatModel, ToolCallingManager toolCallingManager,
                          AgentToolRegistry toolRegistry, AgentSkillService skillService,
                          AiProperties properties, PromptBuilder promptBuilder, AiAdminSettingsService settings,
                          AiUsageService usageService, AiProfileService profileService, AiMemoryService memoryService,
                          @Value("${spring.ai.tools.limits.max-total-tool-calls:10}") int maxToolRounds,
                          @Value("${app.ai.agent.turn-timeout-ms:150000}") long turnTimeoutMs,
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
        this.maxToolRounds = Math.max(1, maxToolRounds);
        this.turnTimeoutMs = Math.max(1L, turnTimeoutMs);
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
                if (round >= maxToolRounds) {
                    prompt = new Prompt(prompt.getInstructions(), withoutTools(prompt.getOptions()));
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
                    driveRound(request.prompt(), 0, deadline, run);
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
     */
    private void driveRound(Prompt prompt, int round, long deadline, AgentRun run) {
        long remaining = remainingNanos(deadline);
        if (remaining <= 0) {
            run.fail(turnTimeout());
            return;
        }
        // Forward each raw chunk as it arrives; the aggregator is only used to inspect the round
        // once it completes. Note it emits a *running* aggregate per chunk (not one final element),
        // so the decision is taken in onComplete with the last snapshot. The timeout is the turn
        // budget that is left, so a stalled provider cannot hold the stream open.
        Flux<ChatResponse> raw = chatModel.stream(prompt)
                .timeout(Duration.ofNanos(remaining))
                .onErrorMap(TimeoutException.class, error -> turnTimeout())
                .doOnNext(response -> emit(run, AiStreamChunk.from(response)));
        AtomicReference<ChatResponse> last = new AtomicReference<>();
        Flux<ChatClientResponse> aggregated = AGGREGATOR.aggregateChatClientResponse(
                raw.map(response -> new ChatClientResponse(response, Map.of())),
                ignored -> {
                });
        run.track(aggregated.subscribe(
                one -> last.set(one.chatResponse()),
                run::fail,
                () -> completeRound(last.get(), prompt, round, deadline, run)));
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
     */
    private void completeRound(ChatResponse response, Prompt prompt, int round, long deadline,
            AgentRun run) {
        if (!hasToolCalls(response) || !hasToolCallbacks(prompt.getOptions())) {
            run.complete();
            return;
        }
        try {
            if (round >= maxToolRounds) {
                // Budget exhausted: one final round without tools so the turn still terminates.
                run.step(AiStreamChunk.Step.thinking());
                driveRound(new Prompt(prompt.getInstructions(), withoutTools(prompt.getOptions())),
                        round + 1, deadline, run);
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
            run.step(AiStreamChunk.Step.thinking());
            driveRound(new Prompt(result.conversationHistory(), prompt.getOptions()), round + 1,
                    deadline, run);
        } catch (RuntimeException ex) {
            run.fail(ex);
        }
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
            callbacks.add(track(tool, run, trace));
            toolNames.add(tool.name());
        }
        String context = userContext(uid, question);
        String system = promptBuilder.agentSystem(
                skillService.catalog(runtime), selected, context, properties.collections(), toolNames);
        String user = promptBuilder.agentUser(question, history, ZonedDateTime.now(turnZone));
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
        return new StepTrackingToolCallback(tool.callback(), run, trace, tool.timeoutMs());
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
     * Loads the user's profile and question-relevant memories for the system prompt.
     *
     * <p>Personalization is best-effort: a database failure must not take down chat, so the turn
     * continues without this block and the failure is logged.</p>
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
