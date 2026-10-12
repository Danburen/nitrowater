package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;

/**
 * Create/update payload for a problem (admin only).
 *
 * <p>Bilingual: the {@code ..._en} fields are optional and display falls back to the Chinese
 * value when absent.</p>
 *
 * @param slug stable slug (optional; auto-generated from {@code titleEn} when present, otherwise
 *             from {@code title} — a conflict with an existing slug returns 409; ignored on update)
 * @param title Chinese title (required)
 * @param titleEn English title (optional)
 * @param difficulty {@code EASY}/{@code MEDIUM}/{@code HARD} (default {@code EASY})
 * @param tagIds ids of existing tags to attach (optional; replaces the problem's current tag set)
 * @param setIds ids of existing problem sets ("题单") to file the problem under
 *             (optional; replaces the problem's current set membership)
 * @param description Chinese markdown statement (required)
 * @param descriptionEn English markdown statement (optional)
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
        String titleEn,
        String difficulty,
        List<Long> tagIds,
        List<Long> setIds,
        @NotBlank String description,
        String descriptionEn,
        String ioMode,
        Map<String, String> templates,
        Map<String, Object> judgeConfig,
        @Positive Integer timeLimitMs,
        @Positive Integer memoryLimitKb,
        @PositiveOrZero Integer rewardPoints,
        Boolean enabled) {
}
