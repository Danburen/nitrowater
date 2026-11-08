package cn.nitrowater.server.service.sandbox.model;

/**
 * Normalised result of a one-off code run.
 *
 * @param stdout captured standard output
 * @param stderr captured standard error (or compiler diagnostics on {@code CE})
 * @param status {@code OK} on success, otherwise {@code CE}/{@code TO}/{@code OL}/{@code RE}/
 *               {@code SG}/{@code SE}
 * @param exitCode process exit code, or {@code null} when killed by a signal
 * @param wallTimeMs wall-clock execution time in milliseconds
 * @param memoryBytes peak memory usage in bytes
 * @param message human-readable failure reason, or {@code null}
 */
public record RunResult(
        String stdout,
        String stderr,
        String status,
        Integer exitCode,
        Long wallTimeMs,
        Long memoryBytes,
        String message) {
}
