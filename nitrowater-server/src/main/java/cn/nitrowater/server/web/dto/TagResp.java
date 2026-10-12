package cn.nitrowater.server.web.dto;

/**
 * A tag view (bilingual via direct {@code _en} columns).
 *
 * <p>{@code nameEn} may be {@code null}; display falls back to {@code name}.</p>
 *
 * @param id tag id
 * @param slug stable tag slug
 * @param name Chinese name
 * @param nameEn English name (nullable)
 * @param kind {@code TOPIC}/{@code COMPANY}
 * @param color optional color
 * @param enabled whether shown to users
 */
public record TagResp(
        Long id,
        String slug,
        String name,
        String nameEn,
        String kind,
        String color,
        Boolean enabled) {
}
