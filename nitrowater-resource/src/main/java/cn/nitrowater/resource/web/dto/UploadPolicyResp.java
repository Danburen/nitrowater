package cn.nitrowater.resource.web.dto;

/**
 * Upload policy response.
 *
 * @param uuid resource identifier (use it for the callback/proxy upload and later reads)
 * @param uploadUrl presigned PUT URL, or {@code null} when the client must use the proxy endpoint
 * @param method HTTP method for {@code uploadUrl}, or {@code null}
 * @param backend backend holding the bytes
 * @param expiresInSeconds URL lifetime in seconds
 */
public record UploadPolicyResp(
        String uuid,
        String uploadUrl,
        String method,
        String backend,
        long expiresInSeconds) {
}
