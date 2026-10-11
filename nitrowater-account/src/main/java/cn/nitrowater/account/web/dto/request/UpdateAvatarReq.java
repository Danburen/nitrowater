package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Bind the canonical avatar resource to the current user.
 *
 * <p>Carries the {@code resource.uuid} returned by nitrowater-resource after an avatar upload;
 * this uuid is stored on {@code user.avatar_resource_uuid} and becomes the OIDC {@code picture}
 * claim. A blank value clears the avatar.</p>
 */
@Data
public class UpdateAvatarReq {

    /** Avatar {@code resource.uuid}; blank/{@code null} clears the avatar. */
    @Size(max = 36)
    private String resourceUuid;
}
