package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create a submission.
 *
 * @param problemId target problem id
 * @param language front-end language id ({@code java}/{@code c}/{@code cpp}/...)
 * @param code source code
 */
public record CreateSubmissionReq(
        @NotNull Long problemId,
        @NotBlank String language,
        @NotBlank String code) {
}
