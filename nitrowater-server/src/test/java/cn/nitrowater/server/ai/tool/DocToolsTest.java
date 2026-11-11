package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.service.ai.ops.AiAdminSettingsService;
import cn.nitrowater.server.config.ai.AiProperties;
import cn.nitrowater.server.service.ai.rag.DocRetriever;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import cn.nitrowater.server.config.ai.WebSearchProperties;
import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.service.ai.tool.DocTools;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.tool.WebSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the deterministic "local docs miss → one web search" fallback. */
class DocToolsTest {

    private final DocRetriever retriever = mock(DocRetriever.class);
    private final AiAdminSettingsService settings = mock(AiAdminSettingsService.class);

    @BeforeEach
    void enablesWebSearch() {
        when(settings.get()).thenReturn(new AiSettings(
                50000, true, true, true, true, true, true, true, ModerationMode.LOG));
    }

    @Test
    void fallsBackToWebOnceWhenLocalCorpusIsEmpty() {
        when(retriever.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        AtomicInteger searches = new AtomicInteger();
        DocTools tools = new DocTools(retriever, properties(), provider(searches, true),
                webProperties(true), settings);
        AgentTrace trace = new AgentTrace();
        ToolContext context = new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));

        String first = tools.searchDocs("今天上海的天气", 5, context);
        String second = tools.searchDocs("今天上海的天气", 5, context);

        assertThat(searches.get()).isEqualTo(1);
        assertThat(first).contains("\"code\":\"tool.ok\"").contains("\"fallback\":\"web\"")
                .contains("\"source\":\"web\"").contains("https://example.com/weather");
        assertThat(second).contains("\"results\":[]");
        assertThat(trace.sources()).anySatisfy(source -> assertThat(source.kind()).isEqualTo("web"));
    }

    @Test
    void keepsLocalResultWhenCorpusHasHits() {
        when(retriever.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(new org.springframework.ai.document.Document("本地内容", Map.of("doc_title", "本地", "url", ""))));
        AtomicInteger searches = new AtomicInteger();
        DocTools tools = new DocTools(retriever, properties(), provider(searches, true),
                webProperties(true), settings);
        ToolContext context = new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, new AgentTrace()));

        String payload = tools.searchDocs("chat-format", 5, context);

        assertThat(searches.get()).isZero();
        assertThat(payload).contains("本地内容").doesNotContain("fallback");
    }

    @Test
    void skipsWebWhenFallbackDisabled() {
        when(retriever.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        AtomicInteger searches = new AtomicInteger();
        DocTools tools = new DocTools(retriever, properties(), provider(searches, true),
                webProperties(false), settings);
        ToolContext context = new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, new AgentTrace()));

        String payload = tools.searchDocs("今天上海的天气", 5, context);

        assertThat(searches.get()).isZero();
        assertThat(payload).contains("\"count\":0").doesNotContain("fallback");
    }

    @Test
    void retriesTheFallbackAfterATransientFailure() {
        when(retriever.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        AtomicInteger searches = new AtomicInteger();
        WebSearchProvider flaky = new WebSearchProvider() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public List<WebSearchResult> search(String query, int count) {
                if (searches.incrementAndGet() == 1) {
                    throw new IllegalStateException("transient");
                }
                return List.of(new WebSearchResult("Weather", "https://example.com/weather", "Sunny"));
            }
        };
        DocTools tools = new DocTools(retriever, properties(), flaky, webProperties(true), settings);
        AgentTrace trace = new AgentTrace();
        ToolContext context = new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));

        String failed = tools.searchDocs("今天上海的天气", 5, context);
        String recovered = tools.searchDocs("今天上海的天气", 5, context);

        assertThat(failed).contains("\"success\":false").contains("\"code\":\"tool.unavailable\"");
        assertThat(recovered).contains("\"code\":\"tool.ok\"").contains("\"fallback\":\"web\"");
        assertThat(searches.get()).isEqualTo(2);
        assertThat(trace.webSearchSuccesses()).isEqualTo(1);
        assertThat(trace.calls()).extracting(ToolCall::status)
                .containsExactly("tool.unavailable", "tool.ok");
    }

    @Test
    void skipsTheFallbackWhenTheTurnBudgetIsAlreadySpent() {
        when(retriever.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        AtomicInteger searches = new AtomicInteger();
        DocTools tools = new DocTools(retriever, properties(), provider(searches, true),
                new WebSearchProperties(true, "test-key", "http://localhost/search", true,
                        3, 1, 2, 0, 5_000),
                settings);
        AgentTrace trace = new AgentTrace();
        trace.recordWebSearchSuccess("今天北京的天气");
        ToolContext context = new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));

        String payload = tools.searchDocs("今天上海的天气", 5, context);

        assertThat(searches.get()).isZero();
        assertThat(payload).contains("\"results\":[]");
    }

    private static AiProperties properties() {
        return new AiProperties(true, 500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                List.of());
    }

    private static WebSearchProperties webProperties(boolean autoFallback) {
        return new WebSearchProperties(true, "test-key", "http://localhost/search", autoFallback,
                3, 3, 2, 0, 5_000);
    }

    private static WebSearchProvider provider(AtomicInteger searches, boolean enabled) {
        return new WebSearchProvider() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public List<WebSearchResult> search(String query, int count) {
                searches.incrementAndGet();
                return List.of(new WebSearchResult("Weather", "https://example.com/weather",
                        "Sunny, 26C. 这是给模型用的长摘要。"));
            }
        };
    }
}
