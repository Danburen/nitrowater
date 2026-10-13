package cn.nitrowater.account.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Replace-all role assignment payload: the target account ends up with exactly {@code roleCodes}.
 *
 * @param roleCodes the complete desired set of role codes (unknown codes are rejected)
 */
public record AssignRolesReq(
        @NotNull(message = "roleCodes is required")
        List<String> roleCodes) {
}
