package cn.nitrowater.server.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Problem detail (hidden test cases excluded).
 *
 * @param id problem id
 * @param slug stable slug
 * @param title title
 * @param difficulty {@code EASY}/{@code MEDIUM}/{@code HARD}
 * @param category category (nullable)
 * @param tags tags
 * @param description markdown statement
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
        String difficulty,
        String category,
        List<String> tags,
        String description,
        String ioMode,
        Map<String, String> templates,
        Map<String, Object> judgeConfig,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        Integer rewardPoints,
        Boolean enabled,
        List<TestCaseResp> sampleCases) {
}
