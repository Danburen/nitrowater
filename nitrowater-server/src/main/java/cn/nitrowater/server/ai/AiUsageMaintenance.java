package cn.nitrowater.server.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Nightly retention for the AI usage counters.
 *
 * <p>Per-user rows exist to enforce and explain a day's budget, so a few weeks of history is enough
 * (default 31 days). The platform-wide sentinel row ({@code uid = 0}) is one row per day, costs
 * nothing, and is what the long-term "how much did the site spend" series reads - it is therefore
 * kept far longer (default 366 days).</p>
 *
 * <p>Each purge is a single statement, so an interrupted run simply resumes the next night.</p>
 */
@Slf4j
@Component
public class AiUsageMaintenance {

    /** Never prune per-user rows inside this floor, even if misconfigured. */
    private static final int MIN_USER_RETENTION_DAYS = 7;
    /** The platform series is the only long-term record; keep at least a month. */
    private static final int MIN_GLOBAL_RETENTION_DAYS = 31;

    private final AiUsageService usage;
    private final int userRetentionDays;
    private final int globalRetentionDays;

    public AiUsageMaintenance(AiUsageService usage,
            @Value("${app.ai.usage.retention-days:31}") int userRetentionDays,
            @Value("${app.ai.usage.global-retention-days:366}") int globalRetentionDays) {
        this.usage = usage;
        this.userRetentionDays = Math.max(MIN_USER_RETENTION_DAYS, userRetentionDays);
        this.globalRetentionDays = Math.max(MIN_GLOBAL_RETENTION_DAYS, globalRetentionDays);
    }

    /** Runs once a day by default; the cron and both windows are configurable. */
    @Scheduled(cron = "${app.ai.usage.cleanup-cron:0 45 4 * * *}")
    public void cleanup() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int users = usage.purgeUserRowsBefore(today.minusDays(userRetentionDays));
        int globals = usage.purgeGlobalRowsBefore(today.minusDays(globalRetentionDays));
        if (users > 0 || globals > 0) {
            log.info("AI usage cleanup: {} per-user rows ({}d) and {} platform rows ({}d) purged",
                    users, userRetentionDays, globals, globalRetentionDays);
        }
    }
}
