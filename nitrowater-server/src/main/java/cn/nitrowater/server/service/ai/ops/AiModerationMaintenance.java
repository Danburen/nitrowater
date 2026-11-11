package cn.nitrowater.server.service.ai.ops;

import cn.nitrowater.server.config.ai.ModerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Nightly lifecycle maintenance for the moderation audit trail.
 *
 * <p>Hit rows exist so an administrator can decide whether the posture is safe to enforce, which
 * only needs a limited lookback. Keeping them forever would grow an unbounded table of
 * user-attributed content flags, so rows older than {@code retention-days} are deleted; the
 * aggregate counts still fit comfortably in the retained window.</p>
 *
 * <p>The cleanup is deliberately not transactional across a delete batch size: the derived delete
 * runs as one statement and an interrupted run simply resumes on the next night.</p>
 */
@Slf4j
@Component
public class AiModerationMaintenance {

    private final AiModerationService moderation;
    private final Duration retention;

    public AiModerationMaintenance(AiModerationService moderation, ModerationProperties properties) {
        this.moderation = moderation;
        this.retention = Duration.ofDays(Math.max(1, properties.retentionDays()));
    }

    /** Runs once a day by default; the cron and the window are both configurable. */
    @Scheduled(cron = "${app.ai.moderation.cleanup-cron:0 30 4 * * *}")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retention);
        int purged = moderation.purgeOlderThan(cutoff);
        if (purged > 0) {
            log.info("AI moderation cleanup: {} hit rows older than {} purged", purged, cutoff);
        }
    }
}
