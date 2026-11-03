package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.model.AiInput;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.service.ai.model.AiStreamChunk;
import cn.nitrowater.server.service.ai.model.AiTokenUsageMeter;
import cn.nitrowater.server.service.ai.AiUsageService;
import cn.nitrowater.server.service.ai.AiUsageTracker;
import cn.nitrowater.server.service.ai.model.ChatTurn;
import cn.nitrowater.server.service.ai.PromptBuilder;
import cn.nitrowater.server.service.ai.AiMemoryService;
import cn.nitrowater.server.service.ai.AiProfileService;
import cn.nitrowater.server.service.ai.model.Skill;
import cn.nitrowater.server.service.ai.SkillRegistry;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import cn.nitrowater.lib.utils.StringUtil;

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

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final AgentToolProviders tools;
    private final WebSearchProvider webSearchProvider;
    private final SkillRegistry skills;
    private final AiProperties properties;
    private final PromptBuilder promptBuilder;
    private final AiAdminSettingsService settings;
    private final AiUsageService usage;
    private final AiProfileService profiles;
    private final AiMemoryService memories;
    private final int maxToolRounds;

    public AiAgentService(ChatModel chatModel, ToolCallingManager toolCallingManager,
            AgentToolProviders tools, WebSearchProvider webSearchProvider, SkillRegistry skills,
            AiProperties properties, PromptBuilder promptBuilder, AiAdminSettingsService settings,
            AiUsageService usage, AiProfileService profiles, AiMemoryService memories,
            @Value("${spring.ai.tools.limits.max-total-tool-calls:10}") int maxToolRounds) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.tools = tools;
        this.webSearchProvider = webSearchProvider;
        this.skills = skills;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.settings = settings;
        this.usage = usage;
        this.profiles = profiles;
        this.memories = memories;
        this.maxToolRounds = Math.max(1, maxToolRounds);
    }

    /**
     * Runs one complete agent turn (no streaming).
     *
     * @param question user question
     * @param history previous turns
     * @param skillName optional selected skill
     * @param allowWeb whether the user allowed web search for this turn
     * @param uid authenticated user id whose daily token quota is charged
     * @param conversationUuid conversation that owns this turn, used as the memory source
     * @return answer + citations + tool trace
     */
    public AgentAnswer answer(String question, List<ChatTurn> history, String skillName,
            boolean allowWeb, long uid, String conversationUuid) {
        AiAdminSettingsService.Settings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        Skill skill = resolveSkill(skillName, runtime);
        AgentTrace trace = traceFor(skill, String.valueOf(uid), conversationUuid);
        // No run: this path has no live channel, so the tools are invoked unwrapped.
        AgentRequest request = request(sanitized, history, skill, allowWeb, trace, runtime, uid, null);
        AiUsageTracker session = usage.start(uid, request.estimatedInputTokens());
        try {
            Prompt prompt = request.prompt();
            ChatResponse response = chatModel.call(prompt);
            int round = 0;
            while (hasToolCalls(response) && hasToolCallbacks(prompt.getOptions())) {
                session.record(usageOf(response));
                if (round >= maxToolRounds) {
                    prompt = new Prompt(prompt.getInstructions(), withoutTools(prompt.getOptions()));
                } else {
                    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
                    if (result.returnDirect()) {
                        break;
                    }
                    prompt = new Prompt(result.conversationHistory(), prompt.getOptions());
                }
                round++;
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
     * @param skillName optional selected skill
     * @param allowWeb whether the user allowed web search for this turn
     * @param uid authenticated user id whose daily token quota is charged
     * @param conversationUuid conversation that owns this turn, used as the memory source
     * @return trace + event stream
     */
    public PreparedAgent prepare(
            String question,
            List<ChatTurn> history,
            String skillName,
            boolean allowWeb,
            long uid,
            String conversationUuid
    ) {
        AiAdminSettingsService.Settings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        Skill skill = resolveSkill(skillName, runtime);
        AgentTrace trace = traceFor(skill, String.valueOf(uid), conversationUuid);
        AgentRun run = new AgentRun();
        AgentRequest request = request(sanitized, history, skill, allowWeb, trace, runtime, uid, run);
        AiUsageTracker session = usage.start(uid, request.estimatedInputTokens());
        Flux<AiStreamChunk> chunks = run.events()
                .doOnSubscribe(ignored -> {
                    run.step(AiStreamChunk.Step.thinking());
                    driveRound(request.prompt(), 0, run);
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
     * @param run    turn stream that receives tokens and steps
     */
    private void driveRound(Prompt prompt, int round, AgentRun run) {
        // Forward each raw chunk as it arrives; the aggregator is only used to inspect the round
        // once it completes. Note it emits a *running* aggregate per chunk (not one final element),
        // so the decision is taken in onComplete with the last snapshot.
        Flux<ChatResponse> raw = chatModel.stream(prompt)
                .doOnNext(response -> emit(run, AiStreamChunk.from(response)));
        AtomicReference<ChatResponse> last = new AtomicReference<>();
        Flux<ChatClientResponse> aggregated = AGGREGATOR.aggregateChatClientResponse(
                raw.map(response -> new ChatClientResponse(response, Map.of())),
                ignored -> {
                });
        run.track(aggregated.subscribe(
                one -> last.set(one.chatResponse()),
                run::fail,
                () -> completeRound(last.get(), prompt, round, run)));
    }

    /**
     * Decides whether the round ended the turn or asked for tools.
     *
     * <p>Tool markers are published by the instrumented callbacks ({@link
     * StepTrackingToolCallback}) one invocation at a time, so this method only has to bracket the
     * rounds: next-round {@code thinking}, budget exhaustion, or completion.</p>
     *
     * @param response aggregated round payload
     * @param prompt   prompt of the round that just finished
     * @param round    zero-based round index
     * @param run      turn stream
     */
    private void completeRound(ChatResponse response, Prompt prompt, int round, AgentRun run) {
        if (!hasToolCalls(response) || !hasToolCallbacks(prompt.getOptions())) {
            run.complete();
            return;
        }
        try {
            if (round >= maxToolRounds) {
                // Budget exhausted: one final round without tools so the turn still terminates.
                run.step(AiStreamChunk.Step.thinking());
                driveRound(new Prompt(prompt.getInstructions(), withoutTools(prompt.getOptions())),
                        round + 1, run);
                return;
            }
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            if (result.returnDirect()) {
                run.complete();
                return;
            }
            run.step(AiStreamChunk.Step.thinking());
            driveRound(new Prompt(result.conversationHistory(), prompt.getOptions()), round + 1, run);
        } catch (RuntimeException ex) {
            run.fail(ex);
        }
    }

    private static void emit(AgentRun run, AiStreamChunk chunk) {
        if (chunk.text().isEmpty() && chunk.usage() == null) {
            return;
        }
        run.emit(chunk);
    }

    /**
     * Builds the prompt and the tool set of one turn.
     *
     * @param question  sanitized question
     * @param history   previous turns
     * @param skill     selected skill, or {@code null}
     * @param allowWeb  whether web search may be offered
     * @param trace     turn trace shared with the tools
     * @param runtime   current admin settings
     * @param uid       authenticated user id
     * @param run       turn stream for live tool markers, or {@code null} on the non-streaming path
     * @return prompt plus the input-token estimate used for quota reservation
     */
    private AgentRequest request(String question, List<ChatTurn> history, Skill skill,
            boolean allowWeb, AgentTrace trace, AiAdminSettingsService.Settings runtime, long uid,
            AgentRun run) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolCallback callback : tools.core().getToolCallbacks()) {
            if (runtime.toolEnabled(callback.getToolDefinition().name())) {
                callbacks.add(track(callback, run, trace));
            }
        }
        if (allowWeb && webSearchProvider.enabled()) {
            for (ToolCallback callback : tools.web().getToolCallbacks()) {
                if (runtime.toolEnabled(callback.getToolDefinition().name())) {
                    callbacks.add(track(callback, run, trace));
                }
            }
        }
        String context = userContext(uid, question);
        String system = promptBuilder.agentSystem(
                runtime.skillsEnabled() ? skills.list() : List.of(), skill, context,
                properties.collections());
        String user = promptBuilder.agentUser(question, history);
        List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
        Prompt prompt = new Prompt(messages, chatOptions(callbacks, trace));
        return new AgentRequest(prompt, AiTokenUsageMeter.estimateTokens(system + user));
    }

    /**
     * @param callback tool to invoke
     * @param run      turn stream, or {@code null} when there is no live channel
     * @param trace    turn trace
     * @return the callback wrapped so its invocations publish step markers
     */
    private static ToolCallback track(ToolCallback callback, AgentRun run, AgentTrace trace) {
        return run == null ? callback : new StepTrackingToolCallback(callback, run, trace);
    }

    /**
     * Loads the user's profile and question-relevant memories for the system prompt.
     *
     * <p>Personalization is best-effort: a database failure must not take down chat, so the turn
     * continues without this block and the failure is logged.</p>
     */
    private String userContext(long uid, String question) {
        try {
            String profile = profiles.promptBlock(uid);
            List<AiMemoryService.MemoryView> recalled = memories.recall(uid, question, 3);
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

    private static AgentTrace traceFor(Skill skill, String userId, String conversationUuid) {
        AgentTrace trace = new AgentTrace(userId, conversationUuid);
        if (skill != null) {
            // The selected skill is inlined into the system prompt, so no loadSkill call occurs;
            // record it here so the UI can still show which workflow was applied.
            trace.addSkill(skill.name());
        }
        return trace;
    }

    private Skill resolveSkill(String skillName, AiAdminSettingsService.Settings runtime) {
        if (StringUtil.isBlank(skillName)) {
            return null;
        }
        if (!runtime.skillsEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI skills are disabled");
        }
        Skill skill = skills.get(skillName);
        if (skill == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown skill: " + skillName);
        }
        return skill;
    }

    private AiAdminSettingsService.Settings requireEnabled() {
        AiAdminSettingsService.Settings runtime = settings.get();
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
