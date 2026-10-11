package cn.nitrowater.server.web.dto;

import java.util.List;

/**
 * Problem list item.
 *
 * @param id problem id
 * @param slug stable slug
 * @param title title
 * @param difficulty {@code EASY}/{@code MEDIUM}/{@code HARD}
 * @param category category (nullable)
 * @param tags tags
 * @param rewardPoints points on first AC
 * @param timeLimitMs per-case time limit
 * @param memoryLimitKb per-case memory limit
 * @param enabled whether shown to users
 */
public record ProblemSummaryResp(
        Long id,
        String slug,
        String title,
        String difficulty,
        String category,
        List<String> tags,
        Integer rewardPoints,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        Boolean enabled) {
}
