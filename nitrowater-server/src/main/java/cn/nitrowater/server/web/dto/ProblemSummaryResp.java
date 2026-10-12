package cn.nitrowater.server.web.dto;

import java.util.List;

/**
 * Problem list item.
 *
 * @param id problem id
 * @param slug stable slug
 * @param title Chinese title
 * @param titleEn English title (nullable; display falls back to {@code title})
 * @param difficulty {@code EASY}/{@code MEDIUM}/{@code HARD}
 * @param tags attached tags
 * @param sets problem sets ("题单") the problem belongs to
 * @param rewardPoints points on first AC
 * @param timeLimitMs per-case time limit
 * @param memoryLimitKb per-case memory limit
 * @param enabled whether shown to users
 */
public record ProblemSummaryResp(
        Long id,
        String slug,
        String title,
        String titleEn,
        String difficulty,
        List<TagResp> tags,
        List<ProblemSetBriefResp> sets,
        Integer rewardPoints,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        Boolean enabled) {
}
