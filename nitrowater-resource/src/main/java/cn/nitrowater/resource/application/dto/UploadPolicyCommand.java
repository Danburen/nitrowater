package cn.nitrowater.resource.application.dto;

import cn.nitrowater.resource.domain.ResourceVisibility;
import cn.nitrowater.resource.domain.StorageBackend;

/**
 * Upload intent passed to {@link cn.nitrowater.resource.application.ResourceService#createUploadPolicy}.
 *
 * <p>Carries domain enums rather than wire strings; the web layer is responsible for translating its
 * request DTO into this command.</p>
 *
 * @param app target application / namespace
 * @param groupKey grouping key within the app (e.g. plugin key), or {@code null}
 * @param fileName original file name (drives extension + MIME resolution)
 * @param contentType client-declared content type (recorded for reference only; never trusted for the
 *                    stored/served MIME)
 * @param sizeBytes client-declared size in bytes
 * @param visibility desired visibility
 * @param backend desired backend, or {@code null} to use the configured default
 */
public record UploadPolicyCommand(
        String app,
        String groupKey,
        String fileName,
        String contentType,
        long sizeBytes,
        ResourceVisibility visibility,
        StorageBackend backend) {
}
