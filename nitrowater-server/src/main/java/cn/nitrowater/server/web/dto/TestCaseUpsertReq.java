package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A test case in a create/update request (admin only).
 *
 * @param idx zero-based case index
 * @param stdin case input (optional)
 * @param expectedOutput expected output (optional)
 * @param isSample whether this case is a sample (default false)
 * @param score optional partial score (default 0)
 */
public record TestCaseUpsertReq(
        @NotNull @PositiveOrZero Integer idx,
        String stdin,
        String expectedOutput,
        Boolean isSample,
        @PositiveOrZero Integer score) {
}
