package cn.nitrowater.server.web.dto;

/**
 * Per-sample outcome of a playground self-test run.
 *
 * @param index zero-based sample index
 * @param input the sample input
 * @param expected the expected output, or {@code null} when the caller supplied none
 * @param actual the actual output produced, or {@code null} when unavailable (compile/fatal failure)
 * @param verdict {@code AC}/{@code WA}/{@code TLE}/{@code OLE}/{@code RE}/{@code CE}/{@code SE}, or
 *                {@code OK} when the sample had no expectation (ran successfully, not judged)
 * @param timeMs wall-clock execution time in milliseconds
 * @param memoryKb peak memory usage in kilobytes
 * @param message human-readable failure reason, or {@code null}
 */
public record PlaygroundCaseResult(
        int index,
        String input,
        String expected,
        String actual,
        String verdict,
        Integer timeMs,
        Integer memoryKb,
        String message) {
}
