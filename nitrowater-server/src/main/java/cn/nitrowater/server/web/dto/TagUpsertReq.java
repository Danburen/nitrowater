package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create/update payload for a tag (admin only).
 *
 * @param slug stable slug (optional; auto-generated from {@code nameEn} when present, otherwise
 *             {@code name}; a conflict returns 409; ignored on update)
 * @param name Chinese name (required)
 * @param nameEn English name (optional)
 * @param kind {@code TOPIC}/{@code COMPANY} (default {@code TOPIC})
 * @param color optional color
 * @param enabled whether shown to users (default true)
 */
public record TagUpsertReq(
        String slug,
        @NotBlank String name,
        String nameEn,
        String kind,
        String color,
        Boolean enabled) {
}
