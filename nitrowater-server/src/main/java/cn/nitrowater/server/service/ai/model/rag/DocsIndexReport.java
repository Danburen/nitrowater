package cn.nitrowater.server.service.ai.model.rag;

import java.time.Instant;
import java.util.List;

/**
 * Administrator view of the documentation index: which corpora and documents are embedded, and
 * which ones drifted away from the mount.
 *
 * @param collection Qdrant collection holding the vectors
 * @param indexed whether the last index run succeeded
 * @param indexing whether a rebuild is running right now
 * @param documents markdown files represented in the report
 * @param chunks chunks stored for those documents
 * @param indexedAt completion time of the last successful run, or {@code null}
 * @param corpora per-corpus roll-up, discovered corpora first
 */
public record DocsIndexReport(String collection, boolean indexed, boolean indexing, int documents,
        int chunks, Instant indexedAt, List<CorpusIndexView> corpora) {

    public DocsIndexReport {
        corpora = corpora == null ? List.of() : List.copyOf(corpora);
    }
}
