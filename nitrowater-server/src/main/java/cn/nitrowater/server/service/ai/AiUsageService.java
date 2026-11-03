package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.exception.AiQuotaExceededException;
import cn.nitrowater.server.service.ai.AiAdminSettingsService.Settings;

import cn.nitrowater.server.infra.entity.AiDailyUsageEntity;
import cn.nitrowater.server.infra.entity.AiDailyUsageId;
import cn.nitrowater.server.infra.repository.AiDailyUsageRepository;
import cn.nitrowater.server.service.ai.model.AiDailyUsage;
import cn.nitrowater.server.service.ai.model.AiTokenUsage;
import cn.nitrowater.server.service.ai.model.AiTokenUsageMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/** Race-safe daily token quota reservation, settlement, inspection and reset. */
@Service
public class AiUsageService {

    private static final Logger LOG = LoggerFactory.getLogger(AiUsageService.class);

    /**
     * Sentinel uid for the all-users counter. Real uids start at 1, and this row reuses the exact
     * same reservation/settlement path as a per-user row, so the global breaker needs no second
     * data structure.
     */
    public static final long GLOBAL_UID = 0L;

    private final AiDailyUsageRepository usageRepository;
    private final AiAdminSettingsService settings;
    private final TransactionTemplate transactions;
    private final long maxOutputReservation;

    public AiUsageService(AiDailyUsageRepository usageRepository, AiAdminSettingsService settings,
            PlatformTransactionManager transactionManager,
            @Value("${spring.ai.openai.chat.max-tokens:1200}") long maxOutputReservation) {
        this.usageRepository = usageRepository;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxOutputReservation = maxOutputReservation > 0 ? maxOutputReservation : 1200L;
    }

    /** Reserves the estimated prompt plus maximum configured answer before calling the provider. */
    @Transactional
    public Reservation reserve(long uid, int estimatedInputTokens) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        Settings runtime = settings.get();
        long limit = runtime.dailyTokenLimit();
        long estimate = Math.max(1, estimatedInputTokens);
        long reservation = Math.addExact(estimate, maxOutputReservation);

        // Lock order is always global-then-user, matching resetToday()'s uid-ascending order, so a
        // concurrent reset and a concurrent turn can never close a lock cycle.
        boolean global = runtime.globalQuotaActive();
        AiDailyUsageEntity globalRow = null;
        if (global) {
            globalRow = rowForUpdate(new AiDailyUsageId(GLOBAL_UID, date));
            long globalUsed = globalRow.getInputTokens() + globalRow.getOutputTokens()
                    + globalRow.getReservedTokens();
            if (globalUsed + reservation > runtime.globalLimit()) {
                throw AiQuotaExceededException.global(runtime.globalLimit(), globalUsed);
            }
        }

        AiDailyUsageEntity row = rowForUpdate(new AiDailyUsageId(uid, date));
        long used = row.getInputTokens() + row.getOutputTokens() + row.getReservedTokens();
        if (used + reservation > limit) {
            throw new AiQuotaExceededException("今日 AI token 额度已用完，请明天再试");
        }

        row.setReservedTokens(row.getReservedTokens() + reservation);
        row.setRequestCount(row.getRequestCount() + 1);
        usageRepository.save(row);
        if (globalRow != null) {
            globalRow.setReservedTokens(globalRow.getReservedTokens() + reservation);
            globalRow.setRequestCount(globalRow.getRequestCount() + 1);
            usageRepository.save(globalRow);
        }
        return new Reservation(uid, date, reservation, estimate, global);
    }

    /** Starts accounting for a model call after its prompt has been assembled. */
    @Transactional
    public AiUsageTracker start(long uid, int estimatedInputTokens) {
        return new Session(this, reserve(uid, estimatedInputTokens), Math.max(0, estimatedInputTokens));
    }

    /** Settles a reservation with provider usage or a marked character-based estimate. */
    public void settle(Reservation reservation, AiTokenUsage usage) {
        transactions.executeWithoutResult(status -> settleInTransaction(reservation, usage));
    }

    /** Releases a reservation when no model request completed. */
    @Transactional
    public void release(Reservation reservation) {
        settle(reservation, new AiTokenUsage(0, 0, false));
    }

    /** @return the authenticated user's UTC-day usage, including an empty snapshot */
    @Transactional(readOnly = true)
    public AiDailyUsage forUser(long uid) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        AiDailyUsageEntity row = usageRepository.findById(new AiDailyUsageId(uid, date)).orElse(null);
        AiDailyUsage usage = row == null
                ? new AiDailyUsage(date, 0, 0, 0, 0, 0, settings.get().dailyTokenLimit(), 0)
                : withLimit(toUsage(row), settings.get().dailyTokenLimit());
        return usage;
    }

    /** @return all recorded users' UTC-day usage, largest consumers first */
    @Transactional(readOnly = true)
    public List<UserUsage> allUsersToday() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        long limit = settings.get().dailyTokenLimit();
        return usageRepository.findByIdUsageDate(date).stream()
                .filter(row -> row.getId().getUid() != GLOBAL_UID)
                .sorted(Comparator.comparingLong((AiDailyUsageEntity row) ->
                                row.getInputTokens() + row.getOutputTokens()).reversed()
                        .thenComparing(row -> row.getId().getUid()))
                .map(row -> new UserUsage(row.getId().getUid(), date, row.getInputTokens(),
                        row.getOutputTokens(), row.getReservedTokens(), row.getRequestCount(),
                        row.getEstimatedRequests(), limit, row.getInputTokens() + row.getOutputTokens()))
                .toList();
    }

    /** Resets today's usage and any leaked reservation for the selected users (all when empty). */
    @Transactional
    public int resetToday(List<Long> uids) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        List<AiDailyUsageEntity> rows = uids == null || uids.isEmpty()
                ? usageRepository.findByIdUsageDate(date).stream()
                        .filter(row -> row.getId().getUid() != GLOBAL_UID).toList()
                : uids.stream().filter(java.util.Objects::nonNull).distinct()
                        .map(uid -> usageRepository.findById(new AiDailyUsageId(uid, date)).orElse(null))
                        .filter(java.util.Objects::nonNull).toList();
        // Deterministic lock order keeps two concurrent resets from deadlocking on each other.
        List<AiDailyUsageEntity> ordered = rows.stream()
                .sorted(Comparator.comparingLong((AiDailyUsageEntity row) -> row.getId().getUid()))
                .toList();
        for (AiDailyUsageEntity row : ordered) {
            AiDailyUsageEntity locked = rowForUpdate(row.getId());
            locked.setInputTokens(0L);
            locked.setOutputTokens(0L);
            // Also clear reservations: an admin reset is the only recovery path for a reservation
            // leaked by a crashed instance, and an in-flight request settles harmlessly afterwards.
            locked.setReservedTokens(0L);
            locked.setRequestCount(0);
            locked.setEstimatedRequests(0);
            usageRepository.save(locked);
        }
        return ordered.size();
    }

    /**
     * @return the platform-wide UTC-day counter plus the breaker state; {@code tripped} includes
     *         in-flight reservations so a pending turn cannot slip past the ceiling
     */
    @Transactional(readOnly = true)
    public GlobalUsage globalToday() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        Settings runtime = settings.get();
        AiDailyUsageEntity row = usageRepository.findById(new AiDailyUsageId(GLOBAL_UID, date)).orElse(null);
        long input = row == null ? 0L : row.getInputTokens();
        long output = row == null ? 0L : row.getOutputTokens();
        long reserved = row == null ? 0L : row.getReservedTokens();
        int requests = row == null ? 0 : row.getRequestCount();
        long limit = runtime.globalLimit();
        boolean enabled = runtime.globalQuotaActive();
        boolean tripped = enabled && input + output + reserved >= limit;
        return new GlobalUsage(date, input, output, reserved, requests, limit, enabled, tripped);
    }

    /**
     * @param days window length (clamped to 1..366)
     * @return platform-wide totals per UTC day, oldest first; days without traffic are omitted
     */
    @Transactional(readOnly = true)
    public List<DailyTotal> dailyTotals(int days) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(Math.max(1, Math.min(366, days)) - 1L);
        return usageRepository.findByIdUidAndIdUsageDateBetween(GLOBAL_UID, from, to).stream()
                .map(row -> new DailyTotal(row.getId().getUsageDate(), row.getInputTokens(),
                        row.getOutputTokens(), row.getRequestCount(), row.getEstimatedRequests()))
                .sorted(Comparator.comparing(DailyTotal::date))
                .toList();
    }

    /** Clears today's platform counter: the manual "lift the breaker" action. */
    @Transactional
    public int resetGlobalToday() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        AiDailyUsageEntity row = rowForUpdate(new AiDailyUsageId(GLOBAL_UID, date));
        row.setInputTokens(0L);
        row.setOutputTokens(0L);
        row.setReservedTokens(0L);
        row.setRequestCount(0);
        row.setEstimatedRequests(0);
        usageRepository.save(row);
        return 1;
    }

    /**
     * Deletes per-user day rows older than the cutoff (retention); the global sentinel row is kept.
     *
     * @param cutoff exclusive upper bound
     * @return deleted rows
     */
    @Transactional
    public int purgeUserRowsBefore(LocalDate cutoff) {
        return usageRepository.deleteUserRowsBefore(cutoff, GLOBAL_UID);
    }

    /**
     * Deletes global sentinel rows older than the cutoff (long-term series retention).
     *
     * @param cutoff exclusive upper bound
     * @return deleted rows
     */
    @Transactional
    public int purgeGlobalRowsBefore(LocalDate cutoff) {
        return usageRepository.deleteGlobalRowsBefore(cutoff, GLOBAL_UID);
    }

    private AiDailyUsageEntity rowForUpdate(AiDailyUsageId id) {
        AiDailyUsageEntity existing = usageRepository.findByIdUidAndIdUsageDate(id.getUid(), id.getUsageDate()).orElse(null);
        if (existing != null) {
            return existing;
        }
        // Only the first row of a (user, UTC day) pair needs the singleton lock: it serializes the
        // insert across application instances so two of them cannot race on the composite key.
        settings.lock();
        return usageRepository.findByIdUidAndIdUsageDate(id.getUid(), id.getUsageDate()).orElseGet(
                () -> usageRepository.saveAndFlush(new AiDailyUsageEntity(id)));
    }

    private void settleInTransaction(Reservation reservation, AiTokenUsage usage) {
        // Global first, then the user row: same lock order as reserve().
        if (reservation.global()) {
            applySettlement(new AiDailyUsageId(GLOBAL_UID, reservation.date()),
                    reservation.reservedTokens(), usage);
        }
        applySettlement(new AiDailyUsageId(reservation.uid(), reservation.date()),
                reservation.reservedTokens(), usage);
    }

    private void applySettlement(AiDailyUsageId id, long reservedTokens, AiTokenUsage usage) {
        AiDailyUsageEntity row = rowForUpdate(id);
        row.setReservedTokens(Math.max(0, row.getReservedTokens() - reservedTokens));
        row.setInputTokens(row.getInputTokens() + Math.max(0, usage.inputTokens()));
        row.setOutputTokens(row.getOutputTokens() + Math.max(0, usage.outputTokens()));
        if (usage.estimated()) {
            row.setEstimatedRequests(row.getEstimatedRequests() + 1);
        }
        usageRepository.save(row);
    }

    private static AiDailyUsage toUsage(AiDailyUsageEntity row) {
        return new AiDailyUsage(row.getId().getUsageDate(), row.getInputTokens(), row.getOutputTokens(),
                row.getReservedTokens(), row.getRequestCount(), row.getEstimatedRequests(), 0,
                row.getInputTokens() + row.getOutputTokens());
    }

    private static AiDailyUsage withLimit(AiDailyUsage usage, long limit) {
        return new AiDailyUsage(usage.date(), usage.inputTokens(), usage.outputTokens(), usage.reservedTokens(),
                usage.requestCount(), usage.estimatedRequests(), limit, usage.totalTokens());
    }

    /** A quota hold pinned to the UTC date on which the request was admitted. */
    public record Reservation(long uid, LocalDate date, long reservedTokens, long estimatedInputTokens,
            boolean global) {
    }

    /** All-users view of one UTC day, including the breaker state. */
    public record GlobalUsage(LocalDate date, long inputTokens, long outputTokens, long reservedTokens,
            int requestCount, long dailyTokenLimit, boolean enabled, boolean tripped) {

        /** @return tokens actually consumed today (reservations excluded) */
        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    /** One day's platform-wide totals, used by the 31-day admin summary. */
    public record DailyTotal(LocalDate date, long inputTokens, long outputTokens, int requestCount,
            int estimatedRequests) {

        /** @return tokens actually consumed that day */
        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    /** Administrator view of one user's UTC-day usage. */
    public record UserUsage(long uid, LocalDate date, long inputTokens, long outputTokens,
            long reservedTokens, int requestCount, int estimatedRequests, long dailyTokenLimit,
            long totalTokens) {
    }

    /** One AI call's mutable accumulator; mutation is limited to its streaming lifecycle. */
    public static final class Session implements AiUsageTracker {

        private final AiUsageService owner;
        private final Reservation reservation;
        private final int estimatedInputTokens;
        private final AiTokenUsageMeter meter = new AiTokenUsageMeter();
        private final StringBuilder output = new StringBuilder();
        private boolean settled;
        private AiTokenUsage result;

        private Session(AiUsageService owner, Reservation reservation, int estimatedInputTokens) {
            this.owner = owner;
            this.reservation = reservation;
            this.estimatedInputTokens = estimatedInputTokens;
        }

        /** Adds provider usage metadata from one stream chunk. */
        @Override
        public synchronized void record(Usage usage) {
            meter.record(usage);
        }

        /** Appends generated answer text for a fallback estimate if the provider omits usage. */
        @Override
        public synchronized void append(String text) {
            if (text != null && !text.isEmpty()) {
                output.append(text);
            }
        }

        /** Settles actual usage once on success, failure, or stream cancellation. */
        @Override
        public synchronized AiTokenUsage finish() {
            if (!settled) {
                settled = true;
                result = meter.snapshot(estimatedInputTokens, output.toString());
                try {
                    owner.settle(reservation, result);
                } catch (RuntimeException ex) {
                    // Accounting must never fail the user's answer or break SSE teardown; the
                    // reservation is left in place and expires with the next day's reset.
                    LOG.warn("Failed to settle AI token usage for uid={} date={}",
                            reservation.uid(), reservation.date(), ex);
                }
            }
            return result;
        }
    }
}
