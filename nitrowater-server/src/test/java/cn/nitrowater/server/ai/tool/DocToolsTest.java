package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.AiProperties;
import cn.nitrowater.server.ai.DocRetriever;
import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.properties.WebSearchProperties;
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
        when(settings.get()).thenReturn(new AiAdminSettingsService.Settings(
                50000, true, true, true, true, true, true, true));
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
        assertThat(first).contains("\"fallback\":\"web\"").contains("\"source\":\"web\"")
                .contains("https://example.com/weather");
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

    private static AiProperties properties() {
        return new AiProperties(true, 500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                "deploy/ai-skills", List.of());
    }

    private static WebSearchProperties webProperties(boolean autoFallback) {
        return new WebSearchProperties(true, "test-key", "http://localhost/search", autoFallback, 3, 5_000);
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
