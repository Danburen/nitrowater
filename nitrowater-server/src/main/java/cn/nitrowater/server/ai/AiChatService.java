package cn.nitrowater.server.ai;

import cn.nitrowater.server.ai.properties.AiProperties;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * NitroBot's RAG mode: corpus-scoped retrieval, grounded prompting and streaming.
 *
 * <p>Retrieval is explicit (instead of an opaque RAG advisor) because the UI has to render the
 * citations before the first token. The retrieval itself is shared with the agent's
 * {@code searchDocs} tool through {@link DocRetriever}.</p>
 */
@Service
public class AiChatService {

    private final ChatModel chatModel;
    private final DocRetriever retriever;
    private final AiProperties properties;
    private final PromptBuilder promptBuilder;
    private final AiAdminSettingsService settings;
    private final AiUsageService usage;

    public AiChatService(ChatModel chatModel, DocRetriever retriever, AiProperties properties,
            PromptBuilder promptBuilder, AiAdminSettingsService settings, AiUsageService usage) {
        this.chatModel = chatModel;
        this.retriever = retriever;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.settings = settings;
        this.usage = usage;
    }

    /**
     * Answers a question in one call (no streaming).
     *
     * @param question user question
     * @param history previous turns
     * @param uid authenticated user id whose daily token quota is charged
     * @return the answer plus citations
     */
    public ChatAnswer answer(String question, List<ChatTurn> history, long uid) {
        AiAdminSettingsService.Settings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        List<Document> documents = runtime.documentSearchEnabled() ? retrieve(sanitized) : List.of();
        List<DocSource> sources = DocSourceMapper.from(documents);
        if (documents.isEmpty() && runtime.documentSearchEnabled()) {
            return new ChatAnswer(PromptBuilder.NO_CONTEXT_ANSWER, List.of(), false,
                    new AiTokenUsage(0, 0, false));
        }
        String system = promptBuilder.system();
        String user = promptBuilder.user(sanitized, documents, history);
        AiUsageTracker session = usage.start(uid, AiTokenUsageMeter.estimateTokens(system + user));
        try {
            ChatResponse response = chatModel.call(prompt(system, user));
            String answer = response == null || response.getResult() == null
                    ? ""
                    : Objects.requireNonNullElse(response.getResult().getOutput().getText(), "").trim();
            session.record(response == null ? null : response.getMetadata().getUsage());
            session.append(answer);
            return new ChatAnswer(answer, sources, !documents.isEmpty(), session.finish());
        } catch (RuntimeException ex) {
            session.finish();
            throw ex;
        }
    }

    /**
     * Retrieves the context and returns a lazy token stream.
     *
     * @param question user question
     * @param history previous turns
     * @param uid authenticated user id whose daily token quota is charged
     * @return citations plus the token stream
     */
    public PreparedChat prepare(String question, List<ChatTurn> history, long uid) {
        AiAdminSettingsService.Settings runtime = requireEnabled();
        String sanitized = AiInput.question(question, properties.maxQuestionChars());
        List<Document> documents = runtime.documentSearchEnabled() ? retrieve(sanitized) : List.of();
        List<DocSource> sources = DocSourceMapper.from(documents);
        if (documents.isEmpty() && runtime.documentSearchEnabled()) {
            return new PreparedChat(List.of(),
                    Flux.just(new AiStreamChunk(PromptBuilder.NO_CONTEXT_ANSWER, null, null, null)), false,
                    java.util.Optional.empty());
        }
        String system = promptBuilder.system();
        String user = promptBuilder.user(sanitized, documents, history);
        AiUsageTracker session = usage.start(uid, AiTokenUsageMeter.estimateTokens(system + user));
        // ChatModel (not ChatClient) on purpose: with tool options in play Spring AI 2.0's
        // ToolCallingAdvisor aggregates the entire stream, which would collapse this answer into a
        // single SSE delta.
        Flux<AiStreamChunk> chunks = chatModel.stream(prompt(system, user))
                .map(AiStreamChunk::from)
                .doOnNext(chunk -> {
                    session.record(chunk.usage());
                    session.append(chunk.text());
                })
                .doFinally(ignored -> session.finish());
        return new PreparedChat(sources, chunks, !documents.isEmpty(),
                java.util.Optional.of(session));
    }

    private static Prompt prompt(String system, String user) {
        List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
        return new Prompt(messages);
    }

    /**
     * @param question sanitized question
     * @return the closest corpus chunks, ordered by relevance
     * @throws ResponseStatusException 503 when the index/embedding endpoint is unavailable
     */
    private List<Document> retrieve(String question) {
        try {
            return retriever.search(question, properties.topK(), properties.similarityThreshold());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI documentation index is unavailable: " + rootMessage(ex), ex);
        }
    }

    private AiAdminSettingsService.Settings requireEnabled() {
        AiAdminSettingsService.Settings runtime = settings.get();
        if (!properties.enabled() || !runtime.aiEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant is disabled");
        }
        if (!runtime.chatEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat mode is disabled");
        }
        return runtime;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return Objects.requireNonNullElse(current.getMessage(), current.getClass().getSimpleName());
    }
}
