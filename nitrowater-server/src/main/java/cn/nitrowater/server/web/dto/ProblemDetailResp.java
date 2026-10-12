package cn.nitrowater.server.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Problem detail (hidden test cases excluded).
 *
 * @param id problem id
 * @param slug stable slug
 * @param title Chinese title
 * @param titleEn English title (nullable; display falls back to {@code title})
 * @param difficulty {@code EASY}/{@code MEDIUM}/{@code HARD}
 * @param tags attached tags
 * @param sets problem sets ("题单") the problem belongs to
 * @param description Chinese markdown statement
 * @param descriptionEn English markdown statement (nullable; display falls back to {@code description})
 * @param ioMode {@code ACM}/{@code LEETCODE}
 * @param templates per-language starter templates
 * @param judgeConfig comparison strategy / method signature
 * @param timeLimitMs per-case time limit
 * @param memoryLimitKb per-case memory limit
 * @param rewardPoints points on first AC
 * @param enabled whether shown to users
 * @param sampleCases the sample test cases (hidden ones are never returned)
 */
public record ProblemDetailResp(
        Long id,
        String slug,
        String title,
        String titleEn,
        String difficulty,
        List<TagResp> tags,
        List<ProblemSetBriefResp> sets,
        String description,
        String descriptionEn,
        String ioMode,
        Map<String, String> templates,
        Map<String, Object> judgeConfig,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        Integer rewardPoints,
        Boolean enabled,
        List<TestCaseResp> sampleCases) {
}
