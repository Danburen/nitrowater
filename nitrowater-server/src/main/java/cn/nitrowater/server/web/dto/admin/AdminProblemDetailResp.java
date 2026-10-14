package cn.nitrowater.server.web.dto.admin;

import cn.nitrowater.server.web.dto.ProblemDetailResp;
import cn.nitrowater.server.web.dto.ProblemSetBriefResp;
import cn.nitrowater.server.web.dto.TagResp;
import cn.nitrowater.server.web.dto.TestCaseResp;

import java.util.List;
import java.util.Map;

/**
 * Admin problem detail (includes hidden test cases).
 *
 * <p>Mirrors {@link ProblemDetailResp} but the {@code testCases} list carries every case —
 * hidden ones included — for the admin editor.</p>
 *
 * @param id problem id
 * @param slug stable slug
 * @param title Chinese title
 * @param titleEn English title (nullable)
 * @param difficulty {@code EASY}/{@code MEDIUM}/{@code HARD}
 * @param tags attached tags
 * @param sets problem sets ("题单") the problem belongs to
 * @param description Chinese markdown statement
 * @param descriptionEn English markdown statement (nullable)
 * @param ioMode {@code ACM}/{@code LEETCODE}
 * @param templates per-language starter templates
 * @param judgeConfig comparison strategy / method signature
 * @param timeLimitMs per-case time limit
 * @param memoryLimitKb per-case memory limit
 * @param rewardPoints points on first AC
 * @param enabled whether shown to users
 * @param testCases every test case, sample and hidden
 */
public record AdminProblemDetailResp(
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
        List<TestCaseResp> testCases) {
}
