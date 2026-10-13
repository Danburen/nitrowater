package cn.nitrowater.account.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create-role payload. The code is normalized to upper-case before uniqueness check / persist.
 *
 * @param code        unique role code
 * @param name        display name
 * @param description optional description
 */
public record RoleCreateReq(
        @NotBlank(message = "role code is required")
        @Size(max = 50, message = "role code must be at most 50 characters")
        String code,

        @NotBlank(message = "role name is required")
        @Size(max = 64, message = "role name must be at most 64 characters")
        String name,

        @Size(max = 255, message = "description must be at most 255 characters")
        String description) {
}
