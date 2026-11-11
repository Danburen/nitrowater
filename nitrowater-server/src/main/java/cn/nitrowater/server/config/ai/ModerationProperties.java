package cn.nitrowater.server.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Content-moderation settings for the assistant ({@code app.ai.moderation.*}).
 *
 * <p>The active posture itself lives in {@code ai_admin_setting.moderation_mode} so it can be
 * flipped from the console without a redeploy; this record only covers the dictionary location and
 * the risk-control thresholds.</p>
 *
 * @param dictLocations violation word list locations ({@code classpath:} or a filesystem path);
 *        a missing file is logged and skipped so a bad mount cannot stop the service
 * @param allowLocations allow-list locations; a term listed here is never reported as a hit
 * @param strikeLimit blocked questions within the strike window before the cooldown starts
 * @param strikeWindowMinutes sliding window used to count strikes
 * @param cooldownMinutes how long the AI endpoints stay paused once the strike limit is reached
 * @param retentionDays how long a hit row is kept; older rows are purged by the scheduled cleanup
 */
@ConfigurationProperties(prefix = "app.ai.moderation")
public record ModerationProperties(
        List<String> dictLocations,
        List<String> allowLocations,
        int strikeLimit,
        int strikeWindowMinutes,
        int cooldownMinutes,
        int retentionDays) {

    public ModerationProperties {
        dictLocations = dictLocations == null || dictLocations.isEmpty()
                ? List.of("classpath:ai/moderation/words.txt")
                : List.copyOf(dictLocations);
        allowLocations = allowLocations == null
                ? List.of("classpath:ai/moderation/allow.txt")
                : List.copyOf(allowLocations);
        strikeLimit = strikeLimit <= 0 ? 5 : strikeLimit;
        strikeWindowMinutes = strikeWindowMinutes <= 0 ? 10 : strikeWindowMinutes;
        cooldownMinutes = cooldownMinutes <= 0 ? 15 : cooldownMinutes;
        retentionDays = retentionDays <= 0 ? 90 : retentionDays;
    }
}
