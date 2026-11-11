package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.service.ai.ops.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.agent.AiAgentService;
import cn.nitrowater.server.service.ai.model.agent.PreparedAgent;
import cn.nitrowater.server.config.ai.AiProperties;
import cn.nitrowater.server.service.ai.agent.AiStreamChunk;
import cn.nitrowater.server.service.ai.model.usage.AiTokenUsage;
import cn.nitrowater.server.service.ai.ops.AiUsageService;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.usage.AiUsageTracker;
import cn.nitrowater.server.service.ai.agent.PromptBuilder;
import cn.nitrowater.server.service.ai.memory.AiMemoryService;
import cn.nitrowater.server.service.ai.memory.AiProfileService;
import cn.nitrowater.server.service.ai.skill.AgentSkillService;
import cn.nitrowater.server.service.ai.tool.AgentToolRegistry;
import cn.nitrowater.server.service.ai.model.agent.AgentTool;
import cn.nitrowater.server.service.ai.model.agent.ToolFeature;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import cn.nitrowater.server.infra.exception.AiTurnTimeoutException;
import cn.nitrowater.server.infra.enums.ModerationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.metadata.Usage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Guards the reason this service bypasses {@code ChatClient}: Spring AI 2.0's
 * {@code ToolCallingAdvisor} aggregates a whole model stream into one element as soon as tool
 * options are present, which made every answer arrive as a single SSE delta.
 *
 * <p>Also guards the step protocol: process markers must be one pair per tool invocation (not one
 * pair per round), carry the tool argument, and never mix into the answer text.</p>
 */
class AiAgentServiceStreamTest {

    private final ToolCallingManager toolCallingManager = mock(ToolCallingManager.class);
    private final AiAdminSettingsService settings = mock(AiAdminSettingsService.class);
    private final AiUsageService usage = mock(AiUsageService.class);

    private AiAgentService service;

    @BeforeEach
    void setUp() {
        when(settings.get()).thenReturn(new AiSettings(
                50_000, true, true, true, true, true, true, true, ModerationMode.LOG));
        when(settings.disabledTools()).thenReturn(Set.of());
        when(usage.start(anyLong(), anyInt())).thenReturn(new NoopTracker());
    }

    @Test
    void forwardsEveryModelChunkInsteadOfOneAggregatedDelta() {
        service = service(new ScriptedChatModel(List.of(chunks("你", "好", "！"))));

        List<String> texts = prepare().chunks()
                .map(AiStreamChunk::text)
                .filter(text -> !text.isEmpty())
                .collectList()
                .block();

        assertThat(texts).containsExactly("你", "好", "！");
    }

    @Test
    void executesToolRoundThenStreamsTheAnswer() {
        AssistantMessage toolCallMessage = callMessage("searchDocs", "{\"query\":\"NitroWater\"}");
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenAnswer(invocation -> new StubToolResult(List.of(
                        new SystemMessage("system"),
                        new UserMessage("你好"),
                        toolCallMessage,
                        new AssistantMessage("这里才是最终回答"))));

        service = service(new ScriptedChatModel(List.of(
                Flux.just(new ChatResponse(List.of(new Generation(toolCallMessage)))),
                chunks("这里才是", "最终回答"))));

        List<String> texts = prepare().chunks()
                .map(AiStreamChunk::text)
                .filter(text -> !text.isEmpty())
                .collectList()
                .block();

        assertThat(texts).containsExactly("这里才是", "最终回答");
        verify(toolCallingManager).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
    }

    /**
     * One marker pair per invocation, not per round: the round boundary markers are {@code
     * thinking}, everything tool-shaped comes from the instrumented callback.
     */
    @Test
    void emitsOneStepPairPerToolInvocation() {
        AssistantMessage toolCallMessage = callMessage("searchDocs", "{\"query\":\"NitroWater\"}");
        stubToolExecution(toolCallMessage);
        service = service(new ScriptedChatModel(List.of(
                Flux.just(new ChatResponse(List.of(new Generation(toolCallMessage)))),
                chunks("最终回答"))));

        List<AiStreamChunk.Step> steps = prepare().chunks()
                .filter(chunk -> chunk.step() != null)
                .map(AiStreamChunk::step)
                .collectList()
                .block();

        assertThat(steps).extracting(AiStreamChunk.Step::phase)
                .containsExactly("thinking", "tool_start", "tool_end", "thinking");
        AiStreamChunk.Step start = steps.get(1);
        assertThat(start.tool()).isEqualTo("searchDocs");
        assertThat(start.argument()).isEqualTo("NitroWater");
        assertThat(start.callId()).isNotBlank();
        AiStreamChunk.Step end = steps.get(2);
        assertThat(end.callId()).isEqualTo(start.callId());
        assertThat(end.status()).isEqualTo("tool.ok");
        assertThat(end.ok()).isTrue();
        assertThat(end.resultCount()).isNotNull();
        assertThat(end.durationMs()).isNotNull();
    }

    /** Two calls in one round must read as two sequential cards, not one batch. */
    @Test
    void keepsTwoToolCallsInOneRoundSeparate() {
        AssistantMessage first = callMessage("searchDocs", "{\"query\":\"one\"}");
        AssistantMessage second = callMessage("searchDocs", "{\"query\":\"two\"}");
        AssistantMessage round = AssistantMessage.builder()
                .toolCalls(concat(first.getToolCalls(), second.getToolCalls()))
                .build();
        stubToolExecution(round);
        service = service(new ScriptedChatModel(List.of(
                Flux.just(new ChatResponse(List.of(new Generation(round)))),
                chunks("最终回答"))));

        List<String> steps = prepare().chunks()
                .filter(chunk -> chunk.step() != null)
                .map(chunk -> chunk.step().phase() + ":" + chunk.step().argument())
                .collectList()
                .block();

        assertThat(steps).containsExactly(
                "thinking:null",
                "tool_start:one", "tool_end:null",
                "tool_start:two", "tool_end:null",
                "thinking:null");
    }

    /**
     * The tool deadline must end the wait, not the turn: the model receives a retryable result and
     * the card closes as {@code tool.timeout} instead of hanging.
     */
    @Test
    void reportsAToolDeadlineAsARetryableResult() {
        AssistantMessage toolCallMessage = callMessage("searchDocs", "{\"query\":\"slow\"}");
        stubToolExecution(toolCallMessage);
        service = service(new ScriptedChatModel(List.of(
                Flux.just(new ChatResponse(List.of(new Generation(toolCallMessage)))),
                chunks("暂时取不到"))), callback("searchDocs", "{\"count\":2}", 1_000L), 100, 150_000L);

        PreparedAgent prepared = prepare();
        List<AiStreamChunk.Step> steps = prepared.chunks()
                .filter(chunk -> chunk.step() != null)
                .map(AiStreamChunk::step)
                .collectList()
                .block();

        AiStreamChunk.Step end = steps.stream()
                .filter(step -> "tool_end".equals(step.phase()))
                .findFirst()
                .orElseThrow();
        assertThat(end.status()).isEqualTo("tool.timeout");
        assertThat(end.ok()).isFalse();
        assertThat(prepared.trace().calls())
                .as("the model must receive the timeout as a recorded result, not an exception")
                .extracting(ToolCall::status)
                .containsExactly("tool.timeout");
    }

    @Test
    void abortsTheTurnWhenTheWallClockBudgetIsSpent() {
        service = service(new ScriptedChatModel(List.of(Flux.never())), 50L);

        assertThatThrownBy(() -> prepare().chunks().collectList().block())
                .as("a stalled model round must end the turn instead of holding the stream open")
                .isInstanceOf(AiTurnTimeoutException.class)
                .hasMessageContaining("50 毫秒");
    }

    @Test
    void keepsModelOptionsAndToolCallbacksInTheRequestBody() {
        ScriptedChatModel chatModel = new ScriptedChatModel(List.of(chunks("好")));
        service = service(chatModel);

        prepare().chunks().blockLast();

        Prompt sent = chatModel.lastPrompt();
        assertThat(sent).isNotNull();
        assertThat(sent.getOptions().getModel())
                .as("mutate() must keep the provider model, otherwise the request has no model")
                .isEqualTo("test-model");
        assertThat(sent.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        ToolCallingChatOptions options = (ToolCallingChatOptions) sent.getOptions();
        assertThat(options.getToolCallbacks()).hasSize(1);
        assertThat(options.getToolContext()).containsKey(AgentTrace.CONTEXT_KEY);
    }

    private PreparedAgent prepare() {
        return service.prepare("你好", List.of(), List.of(), false, 7L, null);
    }

    /**
     * Makes the stubbed manager actually invoke the callbacks carried by the prompt, which is what
     * publishes the per-call step markers in production.
     */
    private void stubToolExecution(AssistantMessage round) {
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(0);
                    ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
                    for (AssistantMessage.ToolCall call : round.getToolCalls()) {
                        for (ToolCallback callback : options.getToolCallbacks()) {
                            if (callback.getToolDefinition().name().equals(call.name())) {
                                callback.call(call.arguments(), null);
                            }
                        }
                    }
                    return new StubToolResult(List.of(
                            new SystemMessage("system"),
                            new UserMessage("你好"),
                            round,
                            new AssistantMessage("最终回答")));
                });
    }

    private AiAgentService service(ChatModel chatModel) {
        return service(chatModel, searchDocsCallback(), 0, 150_000L);
    }

    private AiAgentService service(ChatModel chatModel, long turnTimeoutMs) {
        return service(chatModel, searchDocsCallback(), 0, turnTimeoutMs);
    }

    private AiAgentService service(ChatModel chatModel, ToolCallback callback, int toolTimeoutMs,
            long turnTimeoutMs) {
        AgentToolRegistry tools = mock(AgentToolRegistry.class);
        when(tools.forTurn(any(AiSettings.class), anySet(), anyBoolean()))
                .thenReturn(List.of(new AgentTool(callback, ToolFeature.DOCS, false, toolTimeoutMs)));
        AgentSkillService skills = mock(AgentSkillService.class);
        when(skills.catalog(any(AiSettings.class))).thenReturn(List.of());
        when(skills.resolveSelected(any(), any(AiSettings.class))).thenReturn(List.of());        AiProfileService profiles = mock(AiProfileService.class);
        when(profiles.promptBlock(anyLong())).thenReturn(null);
        AiMemoryService memories = mock(AiMemoryService.class);
        when(memories.recall(anyLong(), any(), anyInt())).thenReturn(List.of());
        return new AiAgentService(chatModel, toolCallingManager, tools,
                skills, properties(), new PromptBuilder(), settings, usage, profiles,
                memories, 10, turnTimeoutMs);
    }

    private static ToolCallback searchDocsCallback() {
        return callback("searchDocs", "{\"count\":2}", 0L);
    }

    /**
     * @param name      tool name
     * @param answer    JSON the tool returns
     * @param delayMs   how long the tool blocks before answering
     * @return a callback with the given behaviour
     */
    private static ToolCallback callback(String name, String answer, long delayMs) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.DefaultToolDefinition.builder()
                        .name(name)
                        .description("search the local documentation")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                return answer;
            }
        };
    }

    private static AssistantMessage callMessage(String tool, String arguments) {
        return AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + tool, "function", tool,
                        arguments)))
                .build();
    }

    private static List<AssistantMessage.ToolCall> concat(List<AssistantMessage.ToolCall> first,
            List<AssistantMessage.ToolCall> second) {
        List<AssistantMessage.ToolCall> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static Flux<ChatResponse> chunks(String... texts) {
        return Flux.fromArray(texts)
                .map(text -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private static AiProperties properties() {
        return new AiProperties(true, 500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                List.of());
    }

    /** Emits one canned script per model round, so a tool round can be followed by an answer. */
    private static final class ScriptedChatModel implements ChatModel {

        private final List<Flux<ChatResponse>> rounds;
        private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
        private int index;

        private ScriptedChatModel(List<Flux<ChatResponse>> rounds) {
            this.rounds = new ArrayList<>(rounds);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("streaming path only");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastPrompt.set(prompt);
            int current = Math.min(index, rounds.size() - 1);
            index++;
            return rounds.get(current);
        }

        private Prompt lastPrompt() {
            return lastPrompt.get();
        }

        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().model("test-model").build();
        }
    }

    private static final class StubToolResult implements ToolExecutionResult {

        private final List<Message> history;

        private StubToolResult(List<Message> history) {
            this.history = history;
        }

        @Override
        public List<Message> conversationHistory() {
            return history;
        }
    }

    /** No-op accounting: this test only cares about delta shape. */
    private static final class NoopTracker implements AiUsageTracker {

        @Override
        public void record(Usage usage) {
        }

        @Override
        public void append(String text) {
        }

        @Override
        public AiTokenUsage finish() {
            return new AiTokenUsage(0, 0, false);
        }
    }
}
