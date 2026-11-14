package cn.nitrowater.resource.service.dto.resp;

import cn.nitrowater.resource.service.dto.ResourceMeta;

/**
 * Resource metadata view.
 *
 * @param uuid resource identifier
 * @param app owning application / namespace
 * @param groupKey grouping key within the app (e.g. plugin key), or {@code null}
 * @param name original file name (display / download name), or {@code null}
 * @param resourceType category name
 * @param mimeType detected mime type
 * @param sizeBytes size in bytes
 * @param visibility {@code PUBLIC} or {@code PRIVATE}
 * @param status lifecycle status
 * @param version version parsed from file info, or {@code null}
 * @param createdAt creation instant (ISO-8601), or {@code null}
 * @param url a presigned read URL when available, else {@code null} (use the read endpoint)
 */
public record ResourceMetaResp(
        String uuid,
        String app,
        String groupKey,
        String name,
        String resourceType,
        String mimeType,
        Long sizeBytes,
        String visibility,
        String status,
        String version,
        String createdAt,
        String url) {

    /**
     * Maps the service-layer view onto the wire shape (enums stringified, instant as ISO-8601).
     *
     * @param meta service metadata view
     * @return the response body
     */
    public static ResourceMetaResp from(ResourceMeta meta) {
        return new ResourceMetaResp(
                meta.uuid(),
                meta.app(),
                meta.groupKey(),
                meta.name(),
                meta.resourceType().name(),
                meta.mimeType(),
                meta.sizeBytes(),
                meta.visibility().name(),
                meta.status().name(),
                meta.version(),
                meta.createdAt() == null ? null : meta.createdAt().toString(),
                meta.url());
    }
}
