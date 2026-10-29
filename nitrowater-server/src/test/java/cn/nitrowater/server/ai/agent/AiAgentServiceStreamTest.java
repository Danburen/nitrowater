package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.AiProperties;
import cn.nitrowater.server.ai.AiStreamChunk;
import cn.nitrowater.server.ai.AiTokenUsage;
import cn.nitrowater.server.ai.AiUsageService;
import cn.nitrowater.server.ai.AiUsageTracker;
import cn.nitrowater.server.ai.PromptBuilder;
import cn.nitrowater.server.ai.skill.SkillRegistry;
import cn.nitrowater.server.ai.tool.WebSearchProvider;
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
import java.util.Map;
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
 */
class AiAgentServiceStreamTest {

    private final ToolCallingManager toolCallingManager = mock(ToolCallingManager.class);
    private final AiAdminSettingsService settings = mock(AiAdminSettingsService.class);
    private final AiUsageService usage = mock(AiUsageService.class);

    private AiAgentService service;

    @BeforeEach
    void setUp() {
        when(settings.get()).thenReturn(new AiAdminSettingsService.Settings(
                50_000, true, true, true, true, true, true, true));
        when(usage.start(anyLong(), anyInt())).thenReturn(new NoopTracker());
    }

    @Test
    void forwardsEveryModelChunkInsteadOfOneAggregatedDelta() {
        service = service(new ScriptedChatModel(List.of(chunks("你", "好", "！"))));

        List<String> texts = service.prepare("你好", List.of(), "", false, 7L)
                .chunks()
                .map(AiStreamChunk::text)
                .filter(text -> !text.isEmpty())
                .collectList()
                .block();

        assertThat(texts).containsExactly("你", "好", "！");
    }

    @Test
    void executesToolRoundThenStreamsTheAnswer() {
        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .content("正在查阅文档：NitroWater")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "searchDocs",
                        "{\"query\":\"NitroWater\"}")))
                .build();
        ChatResponse toolRound = new ChatResponse(List.of(new Generation(toolCallMessage)));
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenAnswer(invocation -> new StubToolResult(List.of(
                        new SystemMessage("system"),
                        new UserMessage("你好"),
                        toolCallMessage,
                        new AssistantMessage("这里才是最终回答"))));

        service = service(new ScriptedChatModel(List.of(
                Flux.just(toolRound),
                chunks("这里才是", "最终回答"))));

        List<String> texts = service.prepare("你好", List.of(), "", false, 7L)
                .chunks()
                .map(AiStreamChunk::text)
                .filter(text -> !text.isEmpty())
                .collectList()
                .block();

        assertThat(texts).containsExactly("正在查阅文档：NitroWater", "这里才是", "最终回答");
        verify(toolCallingManager).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
    }

    @Test
    void emitsLiveActivityStepsAroundTheToolExecution() {
        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .content("正在查阅文档：NitroWater")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "searchDocs",
                        "{\"query\":\"NitroWater\"}")))
                .build();
        ChatResponse toolRound = new ChatResponse(List.of(new Generation(toolCallMessage)));
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenAnswer(invocation -> new StubToolResult(List.of(
                        new SystemMessage("system"),
                        new UserMessage("你好"),
                        toolCallMessage,
                        new AssistantMessage("这里才是最终回答"))));

        service = service(new ScriptedChatModel(List.of(
                Flux.just(toolRound),
                chunks("这里才是", "最终回答"))));

        List<String> steps = service.prepare("你好", List.of(), "", false, 7L)
                .chunks()
                .filter(chunk -> chunk.step() != null)
                .map(chunk -> chunk.step().phase() + ":" + chunk.step().tool())
                .collectList()
                .block();

        assertThat(steps).containsExactly(
                "tool_start:searchDocs", "tool_end:searchDocs", "thinking:null");
    }

    @Test
    void keepsModelOptionsAndToolCallbacksInTheRequestBody() {
        ScriptedChatModel chatModel = new ScriptedChatModel(List.of(chunks("好")));
        service = service(chatModel);

        service.prepare("你好", List.of(), "", false, 7L).chunks().blockLast();

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
                return "{\"count\":0}";
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
        return new AiAgentService(chatModel, toolCallingManager, new AgentToolProviders(core, web),
                webSearch, skills, properties(), new PromptBuilder(), settings, usage, 10);
    }

    private static Flux<ChatResponse> chunks(String... texts) {
        return Flux.fromArray(texts)
                .map(text -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private static AiProperties properties() {
        return new AiProperties(true, 500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                "deploy/ai-skills", List.of());
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
