package cn.nitrowater.account.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Update-role payload. The code is immutable (a rename of the code is rejected for built-in roles
 * and intentionally not exposed here at all).
 *
 * @param name        display name
 * @param description optional description
 */
public record RoleUpdateReq(
        @NotBlank(message = "role name is required")
        @Size(max = 64, message = "role name must be at most 64 characters")
        String name,

        @Size(max = 255, message = "description must be at most 255 characters")
        String description) {
}
