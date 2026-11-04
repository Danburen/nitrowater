package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.service.ai.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.model.agent.AgentToolProviders;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.agent.AiAgentService;
import cn.nitrowater.server.service.ai.model.agent.PreparedAgent;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.service.ai.model.AiStreamChunk;
import cn.nitrowater.server.service.ai.model.AiTokenUsage;
import cn.nitrowater.server.service.ai.AiUsageService;
import cn.nitrowater.server.service.ai.AiUsageTracker;
import cn.nitrowater.server.service.ai.PromptBuilder;
import cn.nitrowater.server.service.ai.AiMemoryService;
import cn.nitrowater.server.service.ai.AiProfileService;
import cn.nitrowater.server.service.ai.SkillRegistry;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
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
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.chat.metadata.Usage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(settings.get()).thenReturn(new AiAdminSettingsService.Settings(
                50_000, true, true, true, true, true, true, true, ModerationMode.LOG));
        when(settings.toolEnabled(any(String.class))).thenReturn(true);
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
        return service.prepare("你好", List.of(), "", false, 7L, null);
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
        ToolCallback searchDocs = new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.DefaultToolDefinition.builder()
                        .name("searchDocs")
                        .description("search the local documentation")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "{\"count\":2}";
            }
        };
        ToolCallbackProvider core = mock(ToolCallbackProvider.class);
        when(core.getToolCallbacks()).thenReturn(new ToolCallback[] { searchDocs });
        ToolCallbackProvider web = mock(ToolCallbackProvider.class);
        when(web.getToolCallbacks()).thenReturn(new ToolCallback[0]);
        WebSearchProvider webSearch = mock(WebSearchProvider.class);
        when(webSearch.enabled()).thenReturn(false);
        SkillRegistry skills = mock(SkillRegistry.class);
        when(skills.list()).thenReturn(List.of());
        AiProfileService profiles = mock(AiProfileService.class);
        when(profiles.promptBlock(anyLong())).thenReturn(null);
        AiMemoryService memories = mock(AiMemoryService.class);
        when(memories.recall(anyLong(), any(), anyInt())).thenReturn(List.of());
        return new AiAgentService(chatModel, toolCallingManager, new AgentToolProviders(core, web),
                webSearch, skills, properties(), new PromptBuilder(), settings, usage, profiles,
                memories, 10);
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
