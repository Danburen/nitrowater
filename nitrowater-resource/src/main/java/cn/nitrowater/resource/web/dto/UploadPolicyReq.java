package cn.nitrowater.resource.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request for an upload policy (presigned URL) or a registered pending resource.
 *
 * @param app target application / namespace (object-key prefix)
 * @param fileName original file name (drives the extension check)
 * @param contentType declared content type
 * @param sizeBytes declared size in bytes
 * @param visibility {@code public} or {@code private} (default {@code private})
 * @param backend optional backend override ({@code s3}/{@code local}); null uses the default
 */
public record UploadPolicyReq(
        @NotBlank String app,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @PositiveOrZero long sizeBytes,
        String visibility,
        String backend) {
}
