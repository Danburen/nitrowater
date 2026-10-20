package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for a playground self-test run (one-off; nothing is persisted).
 *
 * <p>The self-test is a plain "stdin in, stdout out" run: the user supplies one free-form input
 * and gets the program's actual output back. There are no expected outputs, no cases and no
 * verdicts — only compile/runtime status.</p>
 *
 * @param problemId problem to run against (drives the harness/signature and the resource limits)
 * @param language front-end language id ({@code java}, {@code c}, {@code cpp}, {@code python},
 *                 {@code javascript}, {@code typescript} or {@code lua})
 * @param code the source code to run
 * @param stdin the self-test input (a JSON parameter object for LeetCode-style problems, plain
 *              standard input otherwise); may be null/empty
 */
public record PlaygroundRunRequest(
        @NotNull Long problemId,
        @NotBlank String language,
        @NotBlank String code,
        String stdin) {
}
