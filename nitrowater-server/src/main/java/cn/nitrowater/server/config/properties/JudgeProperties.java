package cn.nitrowater.server.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for the judging pipeline ({@code app.judge.*}).
 *
 * @param workers number of background judge consumer threads; this is the effective concurrency
 *                gate on judging and therefore on how many sandbox executions are in flight at
 *                once (kept modest to protect Piston). Clamped to at least {@code 1}.
 */
@ConfigurationProperties(prefix = "app.judge")
public record JudgeProperties(int workers) {
}
