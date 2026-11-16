package cn.nitrowater.server.service.ai.model.rag;

import java.time.Instant;

/**
 * One markdown file as seen by the index inventory: the merge of the corpus mount and the stored
 * index rows.
 *
 * @param corpus logical corpus name
 * @param docPath corpus-relative markdown path
 * @param title first H1, or the file name
 * @param lang {@code zh} or {@code en}
 * @param status how the file relates to the vector index
 * @param chunks chunks currently embedded for it ({@code 0} when never indexed)
 * @param contentSha hash of the last indexed content, or {@code null} when never indexed
 * @param indexedAt last successful index time for this file, or {@code null}
 */
public record IndexedDocView(String corpus, String docPath, String title, String lang,
        DocIndexStatus status, int chunks, String contentSha, Instant indexedAt) {
}
