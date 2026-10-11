package cn.nitrowater.server.sandbox;

/**
 * Normalised result of one Piston execution.
 *
 * <p>Mirrors Piston's two-stage model: {@code compile} is {@code null} for interpreted languages,
 * and {@code run} carries the actual program outcome. Field values are already normalised from
 * Piston's response so callers never touch the raw JSON.</p>
 *
 * @param compile compile-stage outcome, or {@code null} when the language is not compiled
 * @param run run-stage outcome (Piston copies the compile result here on compile failure)
 * @param language the resolved language name echoed by Piston
 * @param version the resolved runtime version echoed by Piston
 */
public record ExecResult(Stage compile, Stage run, String language, String version) {

    /**
     * Outcome of a single Piston stage (compile or run).
     *
     * @param stdout captured standard output
     * @param stderr captured standard error
     * @param output stdout and stderr interleaved as produced
     * @param code process exit code, or {@code null} when killed by a signal
     * @param signal terminating signal name, or {@code null} on normal exit
     * @param status Piston status code ({@code TO}/{@code OL}/{@code EL}/{@code RE}/{@code SG}/
     *               {@code XX}), or {@code null} on success
     * @param message human-readable failure reason, or {@code null}
     * @param cpuTimeMs CPU time used, in milliseconds
     * @param wallTimeMs wall-clock time used, in milliseconds
     * @param memoryBytes peak memory used, in bytes
     */
    public record Stage(
            String stdout,
            String stderr,
            String output,
            Integer code,
            String signal,
            String status,
            String message,
            Long cpuTimeMs,
            Long wallTimeMs,
            Long memoryBytes) {
    }
}
