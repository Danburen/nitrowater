package cn.nitrowater.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the ifdian OpenAPI behind the public sponsor ("鸣谢") list.
 *
 * <p>Both credentials come from the environment ({@code IFDIAN_USER_ID} / {@code IFDIAN_TOKEN}).
 * The token is used only to derive the request signature and is never sent over the wire. Leaving
 * either blank disables the upstream call and the public endpoint answers with an empty page.</p>
 *
 * @param baseUrl base URL of the ifdian open API (its {@code /api/open} root)
 * @param userId public ifdian user id, sent as {@code user_id}
 * @param token signing secret, never transmitted
 * @param connectTimeoutMs TCP connect timeout for ifdian calls
 * @param readTimeoutMs read timeout for ifdian calls
 * @param pageSize number of sponsors per cached page
 * @param refreshInterval cache TTL (also the intended refresh cadence)
 * @param refreshDelayMs scheduler delay between proactive first-page refreshes
 */
@ConfigurationProperties(prefix = "app.ifdian")
public record IfdianProperties(
        String baseUrl,
        String userId,
        String token,
        int connectTimeoutMs,
        int readTimeoutMs,
        int pageSize,
        Duration refreshInterval,
        long refreshDelayMs) {

    /**
     * @return whether both credentials are present, i.e. live calls are possible
     */
    public boolean configured() {
        return userId != null && !userId.isBlank() && token != null && !token.isBlank();
    }

    /**
     * @return effective page size; ifdian allows 1..100, 20 when unset
     */
    public int size() {
        return pageSize > 0 ? Math.min(pageSize, 100) : 20;
    }

    /**
     * @return effective cache TTL, six hours when unset
     */
    public Duration ttl() {
        return refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()
                ? Duration.ofHours(6) : refreshInterval;
    }
}
