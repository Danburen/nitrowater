package cn.nitrowater.server.service.ai.properties;

import cn.nitrowater.server.service.ai.model.DocCollection;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly-typed configuration for the documentation assistant ({@code app.ai.*}).
 *
 * <p>The assistant is a small RAG pipeline: markdown sources are chunked, embedded and stored in
 * Qdrant; a chat question retrieves the closest chunks and asks an OpenAI-compatible chat model to
 * answer strictly from them. Both model endpoints are configured through {@code spring.ai.openai.*}
 * (chat and embedding may point at different vendors).</p>
 *
 * @param enabled master switch; when false every AI endpoint answers 503 and no ingestion runs
 * @param chunkSize target chunk size in tokens (the splitter only splits larger sections)
 * @param minChunkSizeChars chunks shorter than this many characters are dropped
 * @param maxChunksPerDoc hard cap per markdown section, guarding against pathological files
 * @param topK number of chunks retrieved per question
 * @param similarityThreshold minimum cosine similarity for a chunk to be used as context
 * @param maxQuestionChars maximum accepted question length
 * @param maxHistoryTurns number of previous turns forwarded to the model
 * @param ingestOnStartup whether to index automatically when the collection is still empty
 * @param perUserPerMinute sliding-window chat request cap per user, per minute
 * @param collections documentation corpora to index; each entry carries its own corpus name and
 *        markdown location ({@code classpath:} or filesystem)
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        int chunkSize,
        int minChunkSizeChars,
        int maxChunksPerDoc,
        int topK,
        double similarityThreshold,
        int maxQuestionChars,
        int maxHistoryTurns,
        boolean ingestOnStartup,
        int perUserPerMinute,
        List<DocCollection> collections
) {
    public AiProperties {
        collections = collections == null ? List.of() : List.copyOf(collections);
    }
}
