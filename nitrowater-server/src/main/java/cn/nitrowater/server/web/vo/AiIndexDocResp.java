package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.rag.IndexedDocView;

import java.time.Instant;

/**
 * One markdown document in the admin documentation-index report.
 *
 * @param corpus logical corpus name
 * @param docPath corpus-relative markdown path
 * @param title first H1, or the file name
 * @param lang {@code zh} or {@code en}
 * @param status {@code INDEXED}, {@code STALE}, {@code MISSING} or {@code ORPHAN}
 * @param chunks chunks currently embedded for the document
 * @param contentSha hash of the last indexed content, or {@code null} when never indexed
 * @param indexedAt last successful index time for the document, or {@code null}
 */
public record AiIndexDocResp(String corpus, String docPath, String title, String lang, String status,
        int chunks, String contentSha, Instant indexedAt) {

    /**
     * @param view service-side document entry
     * @return the response row
     */
    public static AiIndexDocResp from(IndexedDocView view) {
        return new AiIndexDocResp(view.corpus(), view.docPath(), view.title(), view.lang(),
                view.status().name(), view.chunks(), view.contentSha(), view.indexedAt());
    }
}
