package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.infra.enums.ProblemSetVisibility;
import jakarta.validation.constraints.NotBlank;

/**
 * Create/update payload for a <b>user-owned</b> problem set ("我的题单").
 *
 * <p>Deliberately narrower than {@link ProblemSetUpsertReq}: the owner is taken from the token,
 * and slug / sortOrder / enabled are system-managed. Membership is edited separately via
 * {@link ProblemSetItemsReq}.</p>
 *
 * @param name Chinese name (required)
 * @param description optional description
 * @param visibility PRIVATE (default) or PUBLIC
 */
public record UserProblemSetUpsertReq(
        @NotBlank String name,
        String description,
        ProblemSetVisibility visibility) {
}
