package cn.nitrowater.server.sandbox.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for a one-off code run.
 *
 * @param language front-end language id ({@code java}, {@code c} or {@code cpp})
 * @param code the source code to execute
 * @param stdin optional standard input (may be null/empty)
 */
public record RunRequest(
        @NotBlank String language,
        @NotBlank String code,
        String stdin) {
}
