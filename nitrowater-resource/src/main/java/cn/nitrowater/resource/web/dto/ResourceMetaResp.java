package cn.nitrowater.resource.web.dto;

/**
 * Resource metadata view.
 *
 * @param uuid resource identifier
 * @param app owning application / namespace
 * @param resourceType category name
 * @param mimeType detected mime type
 * @param sizeBytes size in bytes
 * @param visibility {@code PUBLIC} or {@code PRIVATE}
 * @param status lifecycle status
 * @param url a presigned read URL when available, else {@code null} (use the read endpoint)
 */
public record ResourceMetaResp(
        String uuid,
        String app,
        String resourceType,
        String mimeType,
        Long sizeBytes,
        String visibility,
        String status,
        String url) {
}
