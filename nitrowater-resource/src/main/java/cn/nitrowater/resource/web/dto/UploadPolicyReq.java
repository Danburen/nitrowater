package cn.nitrowater.resource.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request for an upload policy (presigned URL) or a registered pending resource.
 *
 * <p>Length limits mirror the {@code resource} table columns, so oversized input is
 * rejected with 400 before it ever reaches the database. Messages are explicit English
 * because this module is not wired to the core i18n bundles.
 *
 * @param app target application / namespace (object-key prefix); max 32 (column {@code app})
 * @param fileName original file name (drives the extension check); max 255
 * @param contentType declared content type; max 128 (column {@code mime_type})
 * @param sizeBytes declared size in bytes
 * @param visibility {@code public} or {@code private} (default {@code private})
 * @param backend optional backend override ({@code s3}/{@code local}); null uses the default
 */
public record UploadPolicyReq(
        @NotBlank(message = "app is required")
        @Size(max = 32, message = "app must be at most 32 characters") String app,
        @NotBlank(message = "fileName is required")
        @Size(max = 255, message = "fileName must be at most 255 characters") String fileName,
        @NotBlank(message = "contentType is required")
        @Size(max = 128, message = "contentType must be at most 128 characters") String contentType,
        @PositiveOrZero(message = "sizeBytes must be >= 0") long sizeBytes,
        String visibility,
        String backend) {
}
