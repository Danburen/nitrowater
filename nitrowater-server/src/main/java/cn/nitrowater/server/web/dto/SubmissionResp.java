package cn.nitrowater.server.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Submission state/result (poll this until {@code status} becomes {@code DONE}).
 *
 * @param id submission id
 * @param problemId problem id
 * @param language language name
 * @param status {@code QUEUED}/{@code RUNNING}/{@code DONE}/{@code SE}
 * @param verdict verdict name, or {@code null} while unjudged
 * @param passed number of passed cases
 * @param total number of cases
 * @param score awarded score
 * @param timeMs max wall time across cases
 * @param memoryKb max memory across cases
 * @param cases per-case results (empty until judged)
 * @param compileMessage compile-error summary (CE only)
 * @param createdAt creation time
 * @param finishedAt judging completion time (null while pending)
 */
public record SubmissionResp(
        Long id,
        Long problemId,
        String language,
        String status,
        String verdict,
        Integer passed,
        Integer total,
        Integer score,
        Integer timeMs,
        Integer memoryKb,
        List<SubmissionCaseResp> cases,
        String compileMessage,
        Instant createdAt,
        Instant finishedAt) {
}
