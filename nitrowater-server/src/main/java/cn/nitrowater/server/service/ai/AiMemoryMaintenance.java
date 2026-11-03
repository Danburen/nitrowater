package cn.nitrowater.server.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Daily lifecycle maintenance for long-term memories.
 *
 * <p>Active memories that have not been used or changed for {@code retention-days} are expired, so
 * stale preferences do not live forever. User-deleted and expired rows are then physically purged
 * after {@code purge-days}, keeping audit history bounded.</p>
 */
@Slf4j
@Component
public class AiMemoryMaintenance {

    private final AiMemoryService memories;
    private final Duration retention;
    private final Duration purgeRetention;

    public AiMemoryMaintenance(AiMemoryService memories,
            @Value("${app.ai.memory.retention-days:180}") long retentionDays,
            @Value("${app.ai.memory.purge-days:30}") long purgeDays) {
        this.memories = memories;
        this.retention = Duration.ofDays(Math.max(1, retentionDays));
        this.purgeRetention = Duration.ofDays(Math.max(1, purgeDays));
    }

    /** Runs once a day by default; both windows are configurable. */
    @Scheduled(cron = "${app.ai.memory.cleanup-cron:0 15 3 * * *}")
    public void cleanup() {
        Instant now = Instant.now();
        int expired = memories.expireStale(now, retention);
        int purged = memories.purge(now, purgeRetention);
        if (expired > 0 || purged > 0) {
            log.info("AI memory cleanup: {} expired, {} purged", expired, purged);
        }
    }
}
