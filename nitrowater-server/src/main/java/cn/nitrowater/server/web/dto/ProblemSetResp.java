package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.infra.enums.ProblemSetVisibility;

/**
 * A full problem-set ("题单") view (admin + public listing).
 *
 * @param id set id
 * @param slug stable set slug (null for user-owned sets)
 * @param name Chinese name
 * @param nameEn English name (nullable; display falls back to {@code name})
 * @param description Chinese description (nullable)
 * @param descriptionEn English description (nullable; display falls back to {@code description})
 * @param color optional color
 * @param sortOrder display order (ascending)
 * @param enabled whether shown to users
 * @param problemCount number of problems in the set
 * @param ownerUid owner uid (null for curated/official sets)
 * @param visibility PRIVATE / PUBLIC (only meaningful for user-owned sets)
 */
public record ProblemSetResp(
        Long id,
        String slug,
        String name,
        String nameEn,
        String description,
        String descriptionEn,
        String color,
        Integer sortOrder,
        Boolean enabled,
        Integer problemCount,
        Long ownerUid,
        ProblemSetVisibility visibility) {
}
