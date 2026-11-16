package cn.nitrowater.server.service.ai.model.rag;

import java.time.Instant;

/**
 * Per-corpus roll-up of the index inventory.
 *
 * @param corpus logical corpus name (the directory name under the docs root)
 * @param description summary shown to the agent, from {@code _corpus.yml}; empty when absent
 * @param sourcePath filesystem directory the corpus was discovered in; empty for a corpus that only
 *        exists in the index (its directory was removed)
 * @param documents markdown files currently on disk plus orphaned index rows
 * @param chunks chunks currently embedded for this corpus
 * @param indexedAt most recent successful index time, or {@code null} when never indexed
 * @param indexed files with an identical content hash in the index
 * @param stale files whose content changed after the last index run
 * @param missing files on disk that are not in the index
 * @param orphan index rows whose markdown file no longer exists
 */
public record CorpusIndexView(String corpus, String description, String sourcePath, int documents,
        int chunks, Instant indexedAt, int indexed, int stale, int missing, int orphan) {
}
