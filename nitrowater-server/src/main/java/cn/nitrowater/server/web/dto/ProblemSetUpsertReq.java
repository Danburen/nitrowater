package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create/update payload for a problem set ("题单", admin only).
 *
 * <p>Bilingual: the {@code ..._en} fields are optional and display falls back to the Chinese
 * value when absent. Membership is edited from the problem side (see
 * {@link ProblemUpsertReq#setIds()}).</p>
 *
 * @param slug stable slug (optional; auto-generated from {@code name} when blank; ignored on update)
 * @param name Chinese name (required)
 * @param nameEn English name (optional)
 * @param description Chinese description (optional)
 * @param descriptionEn English description (optional)
 * @param color optional color
 * @param sortOrder display order (default 0)
 * @param enabled whether shown to users (default true)
 */
public record ProblemSetUpsertReq(
        String slug,
        @NotBlank String name,
        String nameEn,
        String description,
        String descriptionEn,
        String color,
        Integer sortOrder,
        Boolean enabled) {
}
