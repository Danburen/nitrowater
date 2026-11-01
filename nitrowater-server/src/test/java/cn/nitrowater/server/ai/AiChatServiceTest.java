package cn.nitrowater.server.ai;

import cn.nitrowater.server.ai.properties.AiProperties;
import cn.nitrowater.server.infra.enums.ModerationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the no-context path, validation and unavailable-index mapping. */
class AiChatServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final AiAdminSettingsService settings = mock(AiAdminSettingsService.class);
    private final AiUsageService usage = mock(AiUsageService.class);
    private final AiChatService service =
            new AiChatService(chatModel, new DocRetriever(vectorStore, properties()), properties(),
                    new PromptBuilder(), settings, usage);

    @BeforeEach
    void enablesChatFeatures() {
        when(settings.get()).thenReturn(new AiAdminSettingsService.Settings(
                50000, true, true, true, true, true, true, true, ModerationMode.LOG));
    }

    @Test
    void returnsFixedAnswerWithoutCallingTheModelWhenNothingIsRetrieved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChatAnswer answer = service.answer("怎么安装？", List.of(), 7L);

        assertThat(answer.grounded()).isFalse();
        assertThat(answer.answer()).isEqualTo(PromptBuilder.NO_CONTEXT_ANSWER);
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.usage().totalTokens()).isZero();
    }

    @Test
    void rejectsBlankQuestions() {
        assertThatThrownBy(() -> service.answer("   ", List.of(), 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsOverlongQuestions() {
        String question = "x".repeat(2001);

        assertThatThrownBy(() -> service.answer(question, List.of(), 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void mapsVectorStoreFailuresToServiceUnavailable() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("qdrant is down"));

        assertThatThrownBy(() -> service.answer("怎么安装？", List.of(), 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static AiProperties properties() {
        return new AiProperties(true,
                500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                "deploy/ai-skills", java.util.List.of(new DocCollection("velochatx", "docs", "/docs/velochatx/")));
    }
}
