package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Payload for a playground self-test run (one-off; nothing is persisted).
 *
 * @param problemId problem to run against (drives the harness/signature and the resource limits)
 * @param language front-end language id ({@code java}, {@code c}, {@code cpp}, {@code python},
 *                 {@code javascript}, {@code typescript} or {@code lua})
 * @param code the source code to run
 * @param samples user-supplied samples; when null/empty the problem's <em>sample</em> cases are used
 */
public record PlaygroundRunRequest(
        @NotNull Long problemId,
        @NotBlank String language,
        @NotBlank String code,
        List<Sample> samples) {

    /**
     * One user-supplied sample.
     *
     * @param stdin the sample input (a JSON parameter object for LeetCode-style problems)
     * @param expected the expected output, or {@code null} to only display the actual output
     */
    public record Sample(String stdin, String expected) {
    }
}
