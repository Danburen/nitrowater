package cn.nitrowater.resource.application.dto;

import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.ResourceType;
import cn.nitrowater.resource.domain.ResourceVisibility;

/**
 * Service-layer view of a resource's metadata.
 *
 * <p>Uses domain enums (not wire strings) so the application layer stays independent of the HTTP
 * contract; the web layer maps this to its response DTO.</p>
 *
 * @param uuid resource identifier
 * @param app owning application / namespace
 * @param resourceType category
 * @param mimeType server-derived media type
 * @param sizeBytes size in bytes, or {@code null} before the upload completes
 * @param visibility access scope
 * @param status lifecycle status
 * @param url a presigned read URL, or the same-origin service read path when presigning is unsupported
 */
public record ResourceMeta(
        String uuid,
        String app,
        ResourceType resourceType,
        String mimeType,
        Long sizeBytes,
        ResourceVisibility visibility,
        ResourceStatus status,
        String url) {
}
