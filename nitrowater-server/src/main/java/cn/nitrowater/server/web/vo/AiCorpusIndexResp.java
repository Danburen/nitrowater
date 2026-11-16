package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.rag.CorpusIndexView;

import java.time.Instant;

/**
 * One corpus row of the admin documentation-index report.
 *
 * @param corpus logical corpus name (directory under the docs root)
 * @param description summary handed to the agent, from {@code _corpus.yml}
 * @param sourcePath filesystem directory the corpus was discovered in
 * @param documents markdown files on disk plus orphaned index rows
 * @param chunks embedded chunks for this corpus
 * @param indexedAt most recent successful index time, or {@code null}
 * @param indexed files whose content hash still matches the index
 * @param stale files edited after the last index run
 * @param missing files on disk that are not indexed
 * @param orphan index rows whose file no longer exists
 */
public record AiCorpusIndexResp(String corpus, String description, String sourcePath, int documents,
        int chunks, Instant indexedAt, int indexed, int stale, int missing, int orphan) {

    /**
     * @param view service-side roll-up
     * @return the response row
     */
    public static AiCorpusIndexResp from(CorpusIndexView view) {
        return new AiCorpusIndexResp(view.corpus(), view.description(), view.sourcePath(),
                view.documents(), view.chunks(), view.indexedAt(), view.indexed(), view.stale(),
                view.missing(), view.orphan());
    }
}
