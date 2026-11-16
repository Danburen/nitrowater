package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.rag.DocsIndexReport;

import java.time.Instant;
import java.util.List;

/**
 * Admin view of the documentation index: "which documents under the content mount are indexed".
 *
 * <p>Aggregated per corpus; {@link AiIndexDocResp} carries the per-document detail, including the
 * {@code INDEXED / STALE / MISSING / ORPHAN} status that tells an operator whether a content-only
 * deploy needs a re-index.</p>
 *
 * @param collection Qdrant collection holding the vectors
 * @param indexed whether the last index run succeeded
 * @param indexing whether a rebuild is running right now
 * @param documents markdown files represented in the report
 * @param chunks embedded chunks for those documents
 * @param indexedAt completion time of the last successful run, or {@code null}
 * @param corpora per-corpus roll-up
 */
public record AiIndexReportResp(String collection, boolean indexed, boolean indexing, int documents,
        int chunks, Instant indexedAt, List<AiCorpusIndexResp> corpora) {

    /**
     * @param report service-side report
     * @return the response payload
     */
    public static AiIndexReportResp from(DocsIndexReport report) {
        return new AiIndexReportResp(report.collection(), report.indexed(), report.indexing(),
                report.documents(), report.chunks(), report.indexedAt(),
                report.corpora().stream().map(AiCorpusIndexResp::from).toList());
    }
}
