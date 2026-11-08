package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.entity.AiModerationHit;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.service.ai.model.AiGlobalUsage;
import cn.nitrowater.server.service.ai.model.AiModerationHitView;
import cn.nitrowater.server.service.ai.model.AiModerationReport;
import cn.nitrowater.server.service.ai.model.AiReindexResult;
import cn.nitrowater.server.service.ai.model.AiUsageDay;
import cn.nitrowater.server.service.ai.model.AiUsageSummary;
import cn.nitrowater.server.service.ai.model.AiUserUsage;
import cn.nitrowater.server.service.ai.model.AiUserUsageRow;
import cn.nitrowater.server.service.ai.model.IndexStatus;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read models of the AI admin console: usage, moderation hits and the documentation index.
 *
 * <p>These reports join an AI table with the mirrored business profile ({@code BizUser}), so they
 * live here instead of in the controller. Writes (settings, resets, skill CRUD) stay in their own
 * services. Each method returns a service-side model; the controller renders it as a VO.</p>
 */
@Service
public class AiAdminReportService {

    private final AiUsageService usage;
    private final AiModerationService moderation;
    private final DocsIndexService indexService;
    private final BizUserService users;

    public AiAdminReportService(AiUsageService usage, AiModerationService moderation,
            DocsIndexService indexService, BizUserService users) {
        this.usage = usage;
        this.moderation = moderation;
        this.indexService = indexService;
        this.users = users;
    }

    /**
     * Paged per-user UTC-day usage with the mirrored display identity.
     *
     * @param keyword      optional case-insensitive match on username / nickname, or on uid
     * @param onlyExceeded when {@code true}, keep only users at or above their daily token limit
     * @param sort         ordering: {@code tokens} (default, largest first), {@code tokens_asc},
     *                     {@code requests} (most requests first) or {@code uid}
     * @param page         zero-based page index
     * @param size         page size
     * @return one page of matching usage rows
     */
    public Page<AiUserUsageRow> usagePage(String keyword, boolean onlyExceeded, String sort,
            int page, int size) {
        Page<AiUserUsage> rows = usage.pageUsersToday(keyword, onlyExceeded, sort, page, size);
        Map<Long, BizUser> profiles = users.profiles(
                rows.getContent().stream().map(AiUserUsage::uid).toList());
        return rows.map(row -> {
            BizUser profile = profiles.get(row.uid());
            return new AiUserUsageRow(row.uid(),
                    profile == null ? null : profile.getUsername(),
                    profile == null ? null : profile.getNickname(),
                    row);
        });
    }

    /**
     * Platform-wide usage: today's snapshot plus a per-day series from the global sentinel row.
     *
     * @param days window length (clamped to 1..366)
     * @return today's totals, the configured ceiling and the series
     */
    public AiUsageSummary usageSummary(int days) {
        AiGlobalUsage global = usage.globalToday();
        List<AiUsageDay> series = usage.dailyTotals(days).stream()
                .map(day -> new AiUsageDay(day.date(), day.inputTokens(), day.outputTokens(),
                        day.totalTokens(), day.requestCount()))
                .toList();
        return new AiUsageSummary(global.enabled(), global.dailyTokenLimit(), global.totalTokens(),
                global.requestCount(), global.tripped(), global.date(), series);
    }

    /**
     * Content-moderation posture, dictionary size and a page of hits.
     *
     * <p>Input hits are the ones that also drive the risk-control cooldown; output, memory and
     * profile hits are review material for tuning the dictionary, never an account penalty.</p>
     *
     * @param direction optional direction filter
     * @param action optional action filter
     * @param keyword optional username / nickname / uid substring
     * @param from inclusive lower bound on the hit time, or {@code null}
     * @param to exclusive upper bound on the hit time, or {@code null}
     * @param page zero-based page index
     * @param size page size
     * @return the moderation overview with a paged hit list
     */
    public AiModerationReport moderation(ModerationDirection direction, ModerationAction action,
            String keyword, Instant from, Instant to, int page, int size) {
        Page<AiModerationHit> matched = moderation.search(direction, action, keyword, from, to, page, size);
        Map<Long, BizUser> profiles = users.profiles(
                matched.getContent().stream().map(AiModerationHit::getUid).distinct().toList());
        Page<AiModerationHitView> hits = matched.map(row -> {
            BizUser profile = profiles.get(row.getUid());
            return new AiModerationHitView(row.getId(), row.getUid(),
                    profile == null ? null : profile.getUsername(),
                    profile == null ? null : profile.getNickname(),
                    row.getConversationUuid(),
                    row.getDirection().name(), row.getAction().name(),
                    row.getWords(), row.getHitCount(), row.getCreatedAt());
        });
        return new AiModerationReport(moderation.mode().name(), moderation.dictionarySize(),
                moderation.hitsLast24Hours(), hits);
    }

    /**
     * Reloads the violation dictionary and returns the refreshed report in the same round-trip.
     *
     * @param direction optional direction filter
     * @param action optional action filter
     * @param keyword optional username / nickname / uid substring
     * @param from inclusive lower bound on the hit time, or {@code null}
     * @param to exclusive upper bound on the hit time, or {@code null}
     * @param page zero-based page index
     * @param size page size
     * @return the refreshed moderation overview
     */
    public AiModerationReport reloadModeration(ModerationDirection direction, ModerationAction action,
            String keyword, Instant from, Instant to, int page, int size) {
        moderation.reload();
        return moderation(direction, action, keyword, from, to, page, size);
    }

    /**
     * Rebuilds the documentation index (delete corpus, re-embed, upsert).
     *
     * @return the rebuild summary
     * @throws ResponseStatusException 503 when the rebuild failed
     */
    public AiReindexResult reindex() {
        IndexStatus status = indexService.reindex();
        if (!status.indexed()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    status.error() == null ? "Re-index failed" : status.error());
        }
        return new AiReindexResult(status.files(), status.chunks(), status.indexedAt(),
                status.durationMs());
    }
}
