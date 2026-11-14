package cn.nitrowater.resource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Configuration for the resource service ({@code app.resource.*}).
 *
 * @param defaultBackend backend used when a request does not specify one ({@code local} / {@code cos})
 * @param presignEnabled whether clients may receive direct (presigned) storage URLs; {@code false}
 *        routes every read and write through this service's own endpoints, so a single public
 *        origin (the BFF) serves all traffic. Local storage never presigns regardless.
 * @param presignExpirySeconds default lifetime of presigned upload/download URLs (and the basis of
 *        the client cache TTL)
 * @param maxSizeBytes maximum accepted object size for end-user uploads
 * @param pluginMaxSizeBytes maximum accepted plugin artifact size (admin uploads); {@code <= 0} uses
 *        {@link #DEFAULT_PLUGIN_MAX_SIZE_BYTES}
 * @param readCacheSafetyMarginSeconds TTL margin subtracted from a cached presigned GET URL so it is
 *        never served already-expired (defaults to {@code 60} when {@code null} or {@code <= 0})
 * @param cos Tencent COS storage settings (optional; the backend is only registered when credentials are set)
 * @param local local-filesystem backend settings
 * @param internal internal-caller settings (shared-secret token for the {@code /internal/**} endpoints)
 */
@ConfigurationProperties(prefix = "app.resource")
public record ResourceProperties(
        String defaultBackend,
        boolean presignEnabled,
        int presignExpirySeconds,
        long maxSizeBytes,
        long pluginMaxSizeBytes,
        Integer readCacheSafetyMarginSeconds,
        Cos cos,
        Local local,
        Internal internal) {

    /** Default read-cache safety margin in seconds. */
    public static final int DEFAULT_CACHE_SAFETY_MARGIN_SECONDS = 60;

    /** Default cap for plugin artifacts (64 MiB): large enough for shaded jars, still bounded. */
    public static final long DEFAULT_PLUGIN_MAX_SIZE_BYTES = 64L * 1024 * 1024;

    /**
     * @return the effective plugin artifact size cap: the configured value when positive, else
     *         {@link #DEFAULT_PLUGIN_MAX_SIZE_BYTES}
     */
    public long effectivePluginMaxSizeBytes() {
        return pluginMaxSizeBytes > 0 ? pluginMaxSizeBytes : DEFAULT_PLUGIN_MAX_SIZE_BYTES;
    }

    /**
     * @return the effective read-cache safety margin: the configured value when positive, else
     *         {@link #DEFAULT_CACHE_SAFETY_MARGIN_SECONDS}
     */
    public int effectiveCacheSafetyMarginSeconds() {
        if (readCacheSafetyMarginSeconds == null || readCacheSafetyMarginSeconds <= 0) {
            return DEFAULT_CACHE_SAFETY_MARGIN_SECONDS;
        }
        // Never outlive the signature itself.
        return Math.min(readCacheSafetyMarginSeconds, Math.max(1, presignExpirySeconds - 1));
    }

    /** @return whether the internal publish endpoints are enabled (a token is configured) */
    public boolean internalPublishEnabled() {
        return internal != null && StringUtil.isNotBlank(internal.token());
    }

    /** @return the configured internal token, or {@code null} when unset */
    public String internalToken() {
        return internal == null ? null : internal.token();
    }

    /**
     * Tencent COS storage settings. The backend is only registered when
     * {@code secret-id} and {@code secret-key} are both present.
     *
     * @param secretId Tencent Cloud secret id
     * @param secretKey Tencent Cloud secret key
     * @param region COS region, e.g. {@code ap-shanghai}
     * @param bucket COS bucket name (must carry the app id suffix, e.g. {@code mybucket-1250000000})
     */
    public record Cos(String secretId, String secretKey, String region, String bucket) {
    }

    /**
     * Local-filesystem backend settings.
     *
     * @param basePath root directory that object keys are resolved under
     */
    public record Local(String basePath) {
    }

    /**
     * Internal-caller settings: a shared-secret token guarding the {@code /internal/**} endpoints
     * (e.g. seeding platform-owned artefacts). The endpoints are disabled when no token is set.
     *
     * @param token shared secret expected in the {@code X-Internal-Token} request header
     */
    public record Internal(String token) {
    }
}
