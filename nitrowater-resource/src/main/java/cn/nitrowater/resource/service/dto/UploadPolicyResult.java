package cn.nitrowater.resource.service.dto;

import cn.nitrowater.resource.infrastructure.domain.StorageBackend;

/**
 * Result of registering an upload policy.
 *
 * @param uuid resource identifier
 * @param uploadUrl presigned PUT URL, or {@code null} to use the proxy endpoint
 * @param method HTTP method for {@code uploadUrl} ({@code PUT}), or {@code null}
 * @param backend backend that will hold the bytes
 * @param expiresInSeconds URL lifetime
 */
public record UploadPolicyResult(
        String uuid,
        String uploadUrl,
        String method,
        StorageBackend backend,
        long expiresInSeconds) {
}
