package cn.nitrowater.resource.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Confirms a finished direct upload so the resource can be validated and activated.
 *
 * @param uuid resource identifier returned by the upload policy
 */
public record UploadCallbackReq(@NotBlank String uuid) {
}
