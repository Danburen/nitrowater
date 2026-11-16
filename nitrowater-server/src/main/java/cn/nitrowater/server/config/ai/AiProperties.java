package cn.nitrowater.server.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for the documentation assistant ({@code app.ai.*}).
 *
 * <p>The assistant is a small RAG pipeline: markdown sources are chunked, embedded and stored in
 * Qdrant; a chat question retrieves the closest chunks and asks an OpenAI-compatible chat model to
 * answer strictly from them. Both model endpoints are configured through {@code spring.ai.openai.*}
 * (chat and embedding may point at different vendors).</p>
 *
 * <p>The corpora themselves are not configuration: they are discovered at runtime from
 * {@code docs-root}, where every sub-directory is one corpus (see
 * {@link cn.nitrowater.server.service.ai.rag.DocCorpusRegistry}). Dropping a directory of markdown
 * into the mount is therefore enough to publish a corpus, and the corpora stay decoupled from the
 * application image.</p>
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
 * @param docsRoot directory holding the documentation corpora, one sub-directory per corpus
 *        ({@code AI_DOCS_ROOT}); blank means "auto-detect the conventional mount"
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
        String docsRoot
) {
    public AiProperties {
        docsRoot = docsRoot == null ? "" : docsRoot.trim();
    }
}
