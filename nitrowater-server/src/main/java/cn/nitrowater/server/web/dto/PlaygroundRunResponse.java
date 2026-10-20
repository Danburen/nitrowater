package cn.nitrowater.server.web.dto;

/**
 * Result of a playground self-test run: the program's raw output plus run-level status.
 *
 * <p>Nothing is judged — {@code stdout} is simply what the program printed. {@code status}
 * reports the execution outcome so the front-end can show compile errors / timeouts.</p>
 *
 * @param status run state: {@code OK}, otherwise {@code CE}/{@code TO}/{@code OL}/{@code RE}/
 *               {@code SG}/{@code SE}
 * @param stdout captured standard output, or {@code null} when unavailable (compile failure)
 * @param stderr captured standard error (or compiler diagnostics on {@code CE})
 * @param timeMs wall-clock execution time in milliseconds
 * @param memoryKb peak memory usage in kilobytes
 * @param message human-readable failure reason, or {@code null}
 */
public record PlaygroundRunResponse(
        String status,
        String stdout,
        String stderr,
        Integer timeMs,
        Integer memoryKb,
        String message) {
}
