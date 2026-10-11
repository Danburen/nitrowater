package cn.nitrowater.server.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for the code sandbox ({@code app.sandbox.*}).
 *
 * <p>Server-side execution is login-only, so there is a single set of limits (no anonymous tier).</p>
 *
 * @param baseUrl the Piston API base URL (internal network only, e.g. {@code http://piston:2000})
 * @param connectTimeoutMs TCP connect timeout for calls to Piston
 * @param readTimeoutMs read timeout; must exceed compile + run budget so long runs are not cut off
 * @param maxCodeBytes maximum accepted source size, guarding the JSON payload and sandbox input
 * @param globalConcurrency maximum number of in-flight executions allowed at once
 * @param compileTimeoutMs compile-stage timeout passed to Piston (milliseconds)
 * @param perIpPerMinute sliding-window request cap per client IP, per minute
 * @param runTimeoutMs run-stage timeout passed to Piston (milliseconds)
 * @param memoryLimitBytes peak memory cap for the run stage (bytes; Piston expects bytes)
 */
@ConfigurationProperties(prefix = "app.sandbox")
public record SandboxProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxCodeBytes,
        int globalConcurrency,
        int compileTimeoutMs,
        int perIpPerMinute,
        int runTimeoutMs,
        long memoryLimitBytes) {
}
