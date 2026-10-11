package cn.nitrowater.server.web.dto;

/**
 * One judged case result.
 *
 * @param index zero-based case index
 * @param verdict verdict name ({@code AC}/{@code WA}/...)
 * @param timeMs wall time used
 * @param memoryKb peak memory used
 * @param message short reason (null on AC)
 */
public record SubmissionCaseResp(
        Integer index,
        String verdict,
        Integer timeMs,
        Integer memoryKb,
        String message) {
}
