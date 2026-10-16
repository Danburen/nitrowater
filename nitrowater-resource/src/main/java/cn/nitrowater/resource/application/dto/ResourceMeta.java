package cn.nitrowater.resource.application.dto;

import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.ResourceType;
import cn.nitrowater.resource.domain.ResourceVisibility;

import java.time.Instant;

/**
 * Service-layer view of a resource's metadata.
 *
 * <p>Uses domain enums (not wire strings) so the application layer stays independent of the HTTP
 * contract; the web layer maps this to its response DTO.</p>
 *
 * @param uuid resource identifier
 * @param app owning application / namespace
 * @param groupKey grouping key within the app (e.g. plugin key), or {@code null}
 * @param name original file name (display / download name), or {@code null}
 * @param resourceType category
 * @param mimeType server-derived media type
 * @param sizeBytes size in bytes, or {@code null} before the upload completes
 * @param visibility access scope
 * @param status lifecycle status
 * @param version version parsed from file info ({@code file_meta}), or {@code null}
 * @param createdAt creation instant
 * @param url a presigned read URL, or the same-origin service read path when presigning is unsupported
 */
public record ResourceMeta(
        String uuid,
        String app,
        String groupKey,
        String name,
        ResourceType resourceType,
        String mimeType,
        Long sizeBytes,
        ResourceVisibility visibility,
        ResourceStatus status,
        String version,
        Instant createdAt,
        String url) {
}
