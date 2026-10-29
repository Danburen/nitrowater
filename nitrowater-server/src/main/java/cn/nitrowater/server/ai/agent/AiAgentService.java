package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.AiInput;
import cn.nitrowater.server.ai.AiProperties;
import cn.nitrowater.server.ai.AiStreamChunk;
import cn.nitrowater.server.ai.AiTokenUsageMeter;
import cn.nitrowater.server.ai.AiUsageService;
import cn.nitrowater.server.ai.AiUsageTracker;
import cn.nitrowater.server.ai.ChatTurn;
import cn.nitrowater.server.ai.PromptBuilder;
import cn.nitrowater.server.ai.skill.Skill;
import cn.nitrowater.server.ai.skill.SkillRegistry;
import cn.nitrowater.server.ai.tool.WebSearchProvider;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NitroBot's general agent.
 *
 * <p>The assistant talks to {@link ChatModel} directly and runs the tool loop itself instead of
 * using Spring AI's {@code ChatClient} + {@code ToolCallingAdvisor}. That advisor aggregates the
 * whole model stream before emitting anything whenever tool options are present
 * ({@code OpenAiChatOptions} always implements {@code ToolCallingChatOptions}), so every answer
 * would reach the browser as one single SSE delta. Streaming the raw model flux keeps tokens
 * incremental while tool calls are still detected from the aggregated round.
 *
 * <p>Tool call limits are still enforced by the auto-configured {@link ToolCallingManager}
 * ({@code spring.ai.tools.limits.*}); {@link AgentTrace} is carried through the tool context and is
 * complete once the stream finishes.</p>
 */
@Service
public class AiAgentService {

    private static final ChatClientMessageAggregator AGGREGATOR = new ChatClientMessageAggregator();

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final AgentToolProviders tools;
    private final WebSearchProvider webSearchProvider;
    private final SkillRegistry skills;
    private final AiProperties properties;
    private final PromptBuilder promptBuilder;
    private final AiAdminSettingsService settings;
    private final AiUsageService usage;
    private final int maxToolRounds;

    public AiAgentService(ChatModel chatModel, ToolCallingManager toolCallingManager,
            AgentToolProviders tools, WebSearchProvider webSearchProvider, SkillRegistry skills,
            AiProperties properties, PromptBuilder promptBuilder, AiAdminSettingsService settings,
            AiUsageService usage,
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
        this.maxToolRounds = Math.max(1, maxToolRounds);
    }

    /**
     * Runs one complete agent turn (no streaming).
     *
     * @param question user question
     * @param history previous turns
     * @param skillName optional selected skill
     * @param allowWeb whether the user allowed web search for this turn
     * @param uid authenticated user id whose daily token quota is charged (also carried by
     *        {@code runCode})
     * @return answer + citations + tool trace
     */
    public AgentAnswer answer(String question, List<ChatTurn> history, String skillName,
            boolean allowWeb, long uid) {
        AiAdminSettingsService.Settings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        Skill skill = resolveSkill(skillName, runtime);
        AgentTrace trace = traceFor(skill, String.valueOf(uid));
        AgentRequest request = request(sanitized, history, skill, allowWeb, trace, runtime);
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
     * Starts one agent turn and returns the answer token stream. Tool rounds run before the tokens
     * of the next round; the trace is complete when the flux completes.
     *
     * @param question user question
     * @param history previous turns
     * @param skillName optional selected skill
     * @param allowWeb whether the user allowed web search for this turn
     * @param uid authenticated user id whose daily token quota is charged (also carried by
     *        {@code runCode})
     * @return trace + token stream
     */
    public PreparedAgent prepare(
            String question,
            List<ChatTurn> history,
            String skillName,
            boolean allowWeb,
            long uid
    ) {
        AiAdminSettingsService.Settings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        Skill skill = resolveSkill(skillName, runtime);
        AgentTrace trace = traceFor(skill, String.valueOf(uid));
        AgentRequest request = request(sanitized, history, skill, allowWeb, trace, runtime);
        AiUsageTracker session = usage.start(uid, request.estimatedInputTokens());
        Flux<AiStreamChunk> chunks = streamRounds(request.prompt())
                .doOnNext(chunk -> {
                    session.record(chunk.usage());
                    session.append(chunk.text());
                })
                .doFinally(ignored -> session.finish());
        return new PreparedAgent(trace, chunks, session);
    }

    /**
     * Streams the first round and, when the model asked for tools, executes them and streams the
     * next round into the same sink. Visible deltas leave immediately; only tool-call detection
     * waits for the round to finish.
     */
    private Flux<AiStreamChunk> streamRounds(Prompt start) {
        return Flux.defer(() -> {
            Sinks.Many<AiStreamChunk> out = Sinks.many().unicast().onBackpressureBuffer();
            AtomicReference<Disposable> active = new AtomicReference<>();
            // Drive the rounds only once the downstream has subscribed: a synchronous model stream
            // would otherwise emit into a sink that has no subscriber yet.
            return out.asFlux()
                    .doOnSubscribe(ignored -> driveRound(start, 0, out, active))
                    .doOnCancel(() -> {
                        Disposable running = active.get();
                        if (running != null) {
                            running.dispose();
                        }
                    });
        });
    }

    private void driveRound(Prompt prompt, int round, Sinks.Many<AiStreamChunk> out,
            AtomicReference<Disposable> active) {
        // Forward each raw chunk as it arrives; the aggregator is only used to inspect the round
        // once it completes. Note it emits a *running* aggregate per chunk (not one final element),
        // so the decision is taken in onComplete with the last snapshot.
        Flux<ChatResponse> raw = chatModel.stream(prompt)
                .doOnNext(response -> emit(out, AiStreamChunk.from(response)));
        AtomicReference<ChatResponse> last = new AtomicReference<>();
        Flux<ChatClientResponse> aggregated = AGGREGATOR.aggregateChatClientResponse(
                raw.map(response -> new ChatClientResponse(response, Map.of())),
                ignored -> {
                });
        active.set(aggregated.subscribe(
                one -> last.set(one.chatResponse()),
                out::tryEmitError,
                () -> completeRound(last.get(), prompt, round, out, active)));
    }

    private void completeRound(ChatResponse response, Prompt prompt, int round,
            Sinks.Many<AiStreamChunk> out, AtomicReference<Disposable> active) {
        if (!hasToolCalls(response) || !hasToolCallbacks(prompt.getOptions())) {
            out.tryEmitComplete();
            return;
        }
        try {
            if (round >= maxToolRounds) {
                // Budget exhausted: one final round without tools so the turn still terminates.
                out.tryEmitNext(AiStreamChunk.step("thinking", null));
                driveRound(new Prompt(prompt.getInstructions(), withoutTools(prompt.getOptions())),
                        round + 1, out, active);
                return;
            }
            for (String tool : toolNames(response)) {
                out.tryEmitNext(AiStreamChunk.step("tool_start", tool));
            }
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            for (String tool : toolNames(response)) {
                out.tryEmitNext(AiStreamChunk.step("tool_end", tool));
            }
            if (result.returnDirect()) {
                out.tryEmitComplete();
                return;
            }
            out.tryEmitNext(AiStreamChunk.step("thinking", null));
            driveRound(new Prompt(result.conversationHistory(), prompt.getOptions()), round + 1, out,
                    active);
        } catch (RuntimeException ex) {
            out.tryEmitError(ex);
        }
    }

    /**
     * @param response model response that asked for tools
     * @return requested tool names in call order (empty when the frame carries none)
     */
    private static List<String> toolNames(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return List.of();
        }
        return response.getResult().getOutput().getToolCalls().stream()
                .map(AssistantMessage.ToolCall::name)
                .filter(Objects::nonNull)
                .toList();
    }

    private static void emit(Sinks.Many<AiStreamChunk> out, AiStreamChunk chunk) {
        if (chunk.text().isEmpty() && chunk.usage() == null && chunk.step() == null) {
            return;
        }
        out.tryEmitNext(chunk);
    }

    private AgentRequest request(String question, List<ChatTurn> history, Skill skill,
            boolean allowWeb, AgentTrace trace, AiAdminSettingsService.Settings runtime) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolCallback callback : tools.core().getToolCallbacks()) {
            if (runtime.toolEnabled(callback.getToolDefinition().name())) {
                callbacks.add(callback);
            }
        }
        if (allowWeb && webSearchProvider.enabled()) {
            for (ToolCallback callback : tools.web().getToolCallbacks()) {
                if (runtime.toolEnabled(callback.getToolDefinition().name())) {
                    callbacks.add(callback);
                }
            }
        }
        String system = promptBuilder.agentSystem(runtime.skillsEnabled() ? skills.list() : List.of(), skill);
        String user = promptBuilder.agentUser(question, history);
        List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
        Prompt prompt = new Prompt(messages, chatOptions(callbacks, trace));
        return new AgentRequest(prompt, AiTokenUsageMeter.estimateTokens(system + user));
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

    private static AgentTrace traceFor(Skill skill, String userId) {
        AgentTrace trace = new AgentTrace(userId);
        if (skill != null) {
            // The selected skill is inlined into the system prompt, so no loadSkill call occurs;
            // record it here so the UI can still show which workflow was applied.
            trace.addSkill(skill.name());
        }
        return trace;
    }

    private Skill resolveSkill(String skillName, AiAdminSettingsService.Settings runtime) {
        if (skillName == null || skillName.isBlank()) {
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
