package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for renaming an instance (admin only).
 *
 * @param displayName new display name, unique among the owner's live instances
 */
public record InstanceRenameReq(
        @NotBlank @Size(max = 64) String displayName) {
}
