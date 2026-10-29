package cn.nitrowater.server.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for the code sandbox ({@code app.sandbox.*}).
 *
 * <p>Server-side execution is login-only, so there is a single set of limits (no anonymous tier).
 * Runs are serialised per user and queued globally: a request waits for a free slot up to
 * {@code queueWaitMs}, and is rejected once {@code maxQueue} requests are already waiting.</p>
 *
 * @param baseUrl the Piston API base URL (internal network only, e.g. {@code http://piston:2000})
 * @param connectTimeoutMs TCP connect timeout for calls to Piston
 * @param readTimeoutMs read timeout; must exceed compile + run budget so long runs are not cut off
 * @param maxCodeBytes maximum accepted source size, guarding the JSON payload and sandbox input
 * @param globalConcurrency maximum number of in-flight executions allowed at once
 * @param compileTimeoutMs compile-stage timeout passed to Piston (milliseconds)
 * @param perUserPerMinute sliding-window request cap per user, per minute
 * @param perUserPerDay fixed-window request cap per user, per UTC day
 * @param maxQueue maximum number of requests allowed to wait for a slot
 * @param queueWaitMs maximum time a request may wait for a slot before being rejected
 * @param runTimeoutMs run-stage timeout passed to Piston (milliseconds)
 * @param maxOutputBytes maximum bytes returned for each of stdout/stderr
 * @param memoryLimitBytes peak memory cap for the run stage (bytes; Piston expects bytes)
 * @param agentQueueWaitMs how long the agent's {@code runCode} tool may block on a slot
 * @param agentQueueAskWaiting queue depth at which the agent asks the user before waiting
 * @param agentQueueAskMs estimated wait at which the agent asks the user before waiting
 * @param agentQueueEstimateRunMs conservative per-run estimate used for the queue prediction
 */
@ConfigurationProperties(prefix = "app.sandbox")
public record SandboxProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxCodeBytes,
        int globalConcurrency,
        int compileTimeoutMs,
        int perUserPerMinute,
        int perUserPerDay,
        int maxQueue,
        long queueWaitMs,
        int runTimeoutMs,
        int maxOutputBytes,
        long memoryLimitBytes,
        long agentQueueWaitMs,
        int agentQueueAskWaiting,
        long agentQueueAskMs,
        long agentQueueEstimateRunMs) {
}
