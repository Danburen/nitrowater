package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Replace the ordered membership of a problem set.
 *
 * @param problemIds the problem ids in display order (whole list replaces the previous membership)
 */
public record ProblemSetItemsReq(@NotNull List<Long> problemIds) {
}
