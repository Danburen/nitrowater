package cn.nitrowater.resource.web.dto;

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
}
