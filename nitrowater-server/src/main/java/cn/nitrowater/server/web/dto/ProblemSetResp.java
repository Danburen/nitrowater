package cn.nitrowater.server.web.dto;

/**
 * A full problem-set ("题单") view (admin + public listing).
 *
 * @param id set id
 * @param slug stable set slug
 * @param name Chinese name
 * @param nameEn English name (nullable; display falls back to {@code name})
 * @param description Chinese description (nullable)
 * @param descriptionEn English description (nullable; display falls back to {@code description})
 * @param color optional color
 * @param sortOrder display order (ascending)
 * @param enabled whether shown to users
 * @param problemCount number of problems in the set
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
        Integer problemCount) {
}
