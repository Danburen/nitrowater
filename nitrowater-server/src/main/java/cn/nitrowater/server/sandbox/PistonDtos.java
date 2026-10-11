package cn.nitrowater.server.sandbox;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire-format records for Piston's v2 API.
 *
 * <p>Kept package-private on purpose: Piston's JSON shape must never leak past
 * {@link PistonClient}. Property names use {@link JsonProperty} because Piston speaks snake_case.</p>
 */
final class PistonDtos {

    private PistonDtos() {
    }

    /**
     * Piston {@code POST /api/v2/execute} request body.
     *
     * @param language runtime name or alias
     * @param version SemVer selector for the runtime
     * @param files files to write into the job context (first one is the main file)
     * @param stdin standard input text
     * @param runTimeout run-stage wall-time cap in milliseconds
     * @param compileTimeout compile-stage wall-time cap in milliseconds
     * @param runMemoryLimit run-stage memory cap in bytes ({@code -1} for no limit)
     * @param compileMemoryLimit compile-stage memory cap in bytes ({@code -1} for no limit)
     */
    record ExecuteRequest(
            String language,
            String version,
            List<File> files,
            String stdin,
            @JsonProperty("run_timeout") Integer runTimeout,
            @JsonProperty("compile_timeout") Integer compileTimeout,
            @JsonProperty("run_memory_limit") Long runMemoryLimit,
            @JsonProperty("compile_memory_limit") Long compileMemoryLimit) {
    }

    /** A source file uploaded into the job context. */
    record File(String name, String content) {
    }

    /** Piston execute response envelope. */
    record ExecuteResponse(String language, String version, Stage compile, Stage run) {
    }

    /**
     * One Piston stage result.
     *
     * @param stdout standard output
     * @param stderr standard error
     * @param output interleaved output
     * @param code exit code (null when signalled)
     * @param signal terminating signal name
     * @param status two-letter status ({@code TO}/{@code OL}/{@code EL}/{@code RE}/{@code SG}/{@code XX})
     * @param message human-readable failure reason
     * @param cpuTime CPU time in milliseconds
     * @param wallTime wall-clock time in milliseconds
     * @param memory peak memory in bytes
     */
    record Stage(
            String stdout,
            String stderr,
            String output,
            Integer code,
            String signal,
            String status,
            String message,
            @JsonProperty("cpu_time") Long cpuTime,
            @JsonProperty("wall_time") Long wallTime,
            Long memory) {
    }
}
