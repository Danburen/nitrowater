package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;

/**
 * Create/update payload for a problem (admin only).
 *
 * @param slug stable slug (optional; auto-generated from {@code title} when blank, ignored on update)
 * @param title title (required)
 * @param difficulty {@code EASY}/{@code MEDIUM}/{@code HARD} (default {@code EASY})
 * @param category category (optional)
 * @param tags tags (optional)
 * @param description markdown statement (required)
 * @param ioMode {@code ACM}/{@code LEETCODE} (default {@code ACM})
 * @param templates per-language starter templates (optional)
 * @param judgeConfig comparison strategy / method signature (optional)
 * @param timeLimitMs per-case time limit (default 5000)
 * @param memoryLimitKb per-case memory limit (default 262144)
 * @param rewardPoints points on first AC (default 0)
 * @param enabled whether shown to users (default true)
 */
public record ProblemUpsertReq(
        String slug,
        @NotBlank String title,
        String difficulty,
        String category,
        List<String> tags,
        @NotBlank String description,
        String ioMode,
        Map<String, String> templates,
        Map<String, Object> judgeConfig,
        @Positive Integer timeLimitMs,
        @Positive Integer memoryLimitKb,
        @PositiveOrZero Integer rewardPoints,
        Boolean enabled) {
}
