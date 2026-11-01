package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.properties.WebSearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the turn budget, query de-duplication and the token-lean model payload. */
class WebSearchToolTest {

    @Test
    void asksForTheConfiguredResultCountAndReturnsRemainingBudget() {
        WebSearchProvider provider = mock(WebSearchProvider.class);
        when(provider.enabled()).thenReturn(true);
        when(provider.search(anyString(), anyInt())).thenAnswer(call -> hits(call.getArgument(1)));
        WebSearchTool tool = new WebSearchTool(provider, properties(3, 3));
        AgentTrace trace = new AgentTrace();

        String payload = tool.webSearch("spring boot 4 release notes", context(trace));

        assertThat(payload)
                .contains("\"count\":3")
                .contains("\"remaining\":2")
                .contains("\"hint\"")
                .doesNotContain("\"snippet\"");
        verify(provider).search("spring boot 4 release notes", 3);
        assertThat(trace.sources()).hasSize(3);
    }

    @Test
    void failedCallDoesNotConsumeTheBudget() {
        WebSearchProvider provider = mock(WebSearchProvider.class);
        when(provider.enabled()).thenReturn(true);
        when(provider.search(anyString(), anyInt()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(hits(1));
        WebSearchTool tool = new WebSearchTool(provider, properties(3, 3));
        AgentTrace trace = new AgentTrace();

        String failed = tool.webSearch("recent release", context(trace));
        String recovered = tool.webSearch("recent release", context(trace));

        assertThat(failed).contains("\"retryable\":true").contains("\"remaining\":3");
        assertThat(recovered).contains("\"remaining\":2");
        assertThat(trace.webSearchSuccesses()).isEqualTo(1);
    }

    @Test
    void rejectsARepeatedQueryWithoutCallingTheProviderAgain() {
        WebSearchProvider provider = mock(WebSearchProvider.class);
        when(provider.enabled()).thenReturn(true);
        when(provider.search(anyString(), anyInt())).thenAnswer(call -> hits(1));
        WebSearchTool tool = new WebSearchTool(provider, properties(3, 3));
        AgentTrace trace = new AgentTrace();

        tool.webSearch("Java 21", context(trace));
        String duplicate = tool.webSearch("  java   21 ", context(trace));

        assertThat(duplicate).contains("duplicate query").contains("\"remaining\":2");
        verify(provider, times(1)).search(anyString(), anyInt());
    }

    @Test
    void stopsAtTheConfiguredSuccessBudget() {
        WebSearchProvider provider = mock(WebSearchProvider.class);
        when(provider.enabled()).thenReturn(true);
        when(provider.search(anyString(), anyInt())).thenAnswer(call -> hits(1));
        WebSearchTool tool = new WebSearchTool(provider, properties(3, 1));
        AgentTrace trace = new AgentTrace();

        String first = tool.webSearch("first gap", context(trace));
        String second = tool.webSearch("second gap", context(trace));

        assertThat(first).contains("\"remaining\":0");
        assertThat(second).contains("limit reached").contains("\"remaining\":0");
        verify(provider, times(1)).search(anyString(), anyInt());
    }

    private static ToolContext context(AgentTrace trace) {
        return new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));
    }

    private static WebSearchProperties properties(int maxResults, int maxSuccesses) {
        return new WebSearchProperties(true, "test-key", "http://localhost/search", true,
                maxResults, maxSuccesses, 2, 0, 5_000);
    }

    private static List<WebSearchResult> hits(int count) {
        List<WebSearchResult> results = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            results.add(new WebSearchResult("Result " + i, "https://example.com/" + i,
                    "Summary " + i + " with enough detail to answer the question."));
        }
        return results;
    }
}
