package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.service.ai.DocChunkBuilder;
import cn.nitrowater.server.service.ai.model.agent.AgentDoc;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies stable citation numbering and deduplication. */
class AgentTraceTest {

    @Test
    void givesEachChunkItsOwnCitationAndReusesSeenChunks() {
        AgentTrace trace = new AgentTrace();
        Document first = document("guide/config/main.md", "聊天格式", "第一段");
        Document second = document("guide/config/main.md", "聊天格式", "第二段");

        List<AgentDoc> a = trace.addDocuments(List.of(first, second));
        List<AgentDoc> b = trace.addDocuments(List.of(first));

        assertThat(a).extracting(AgentDoc::index).containsExactly(1, 2);
        assertThat(b).extracting(AgentDoc::index).containsExactly(1);
        assertThat(trace.sources()).hasSize(2);
    }

    @Test
    void webResultsGetTheirOwnCitations() {
        AgentTrace trace = new AgentTrace();
        trace.addDocuments(List.of(document("a.md", "A", "text")));

        AgentDoc web = trace.addWebResult("Example", "https://example.com", "snippet");
        AgentDoc duplicate = trace.addWebResult("Example again", "https://example.com", "snippet");

        assertThat(web.index()).isEqualTo(2);
        assertThat(duplicate.index()).isEqualTo(2);
        assertThat(trace.sources()).extracting(source -> source.kind()).containsExactly("doc", "web");
    }

    @Test
    void recordsToolsAndSkills() {
        AgentTrace trace = new AgentTrace();
        trace.recordTool("searchDocs", "chat-format", 4);
        trace.addSkill("config-walkthrough");

        assertThat(trace.calls()).containsExactly(new ToolCall("searchDocs", "chat-format", 4));
        assertThat(trace.skills()).containsExactly("config-walkthrough");
        assertThat(trace.hasSources()).isFalse();
    }

    @Test
    void carriesTheUserAndTracksRunCodeAttempts() {
        AgentTrace trace = new AgentTrace("u1");
        trace.recordTool("runCode", "java · class Main {}", 0, "compile_error", "编译失败");

        assertThat(trace.userId()).isEqualTo("u1");
        assertThat(trace.nextRunCodeAttempt()).isEqualTo(1);
        assertThat(trace.nextRunCodeAttempt()).isEqualTo(2);
        assertThat(trace.runCodeAttempts()).isEqualTo(2);
        assertThat(trace.calls()).containsExactly(
                new ToolCall("runCode", "java · class Main {}", 0, "compile_error", "编译失败"));
    }

    @Test
    void tracksSuccessfulWebSearchesAgainstOneBudgetAndRemembersQueries() {
        AgentTrace trace = new AgentTrace();

        assertThat(trace.remainingWebSearches(3)).isEqualTo(3);
        assertThat(trace.hasSearched("Java 21")).isFalse();

        trace.recordWebSearchSuccess("Java 21");

        assertThat(trace.hasSearched(" java   21 ")).isTrue();
        assertThat(trace.remainingWebSearches(3)).isEqualTo(2);

        trace.recordWebSearchSuccess("Spring Boot 4 release notes");
        trace.recordWebSearchSuccess("DeepSeek pricing");

        assertThat(trace.webSearchSuccesses()).isEqualTo(3);
        assertThat(trace.remainingWebSearches(3)).isZero();
    }

    @Test
    void releasesTheFallbackClaimOnlyWhenAsked() {
        AgentTrace trace = new AgentTrace();

        assertThat(trace.claimWebFallback()).isTrue();
        assertThat(trace.claimWebFallback()).isFalse();

        trace.releaseWebFallback();

        assertThat(trace.claimWebFallback()).isTrue();
    }

    private static Document document(String path, String section, String text) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(
                        DocChunkBuilder.META_DOC_PATH, path,
                        DocChunkBuilder.META_SECTION, section,
                        DocChunkBuilder.META_DOC_TITLE, "标题",
                        DocChunkBuilder.META_URL, "/plugin/velochatx/docs/a.html"))
                .build();
    }
}
