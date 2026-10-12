package cn.nitrowater.server.web.dto;

/**
 * A lightweight problem-set ("题单") view embedded in problem responses.
 *
 * @param id set id
 * @param slug stable set slug
 * @param name Chinese name
 * @param nameEn English name (nullable; display falls back to {@code name})
 * @param color optional color
 */
public record ProblemSetBriefResp(
        Long id,
        String slug,
        String name,
        String nameEn,
        String color) {
}
