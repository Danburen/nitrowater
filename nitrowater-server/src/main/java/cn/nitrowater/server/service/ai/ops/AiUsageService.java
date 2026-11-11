package cn.nitrowater.server.service.ai.ops;

import cn.nitrowater.server.infra.exception.AiQuotaExceededException;

import cn.nitrowater.server.infra.entity.AiDailyUsageEntity;
import cn.nitrowater.server.infra.entity.AiDailyUsageId;
import cn.nitrowater.server.infra.repository.AiDailyUsageRepository;
import cn.nitrowater.server.service.ai.model.admin.*;
import cn.nitrowater.server.service.ai.model.usage.*;
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

import cn.nitrowater.server.infra.specification.AiUsageSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static cn.nitrowater.server.infra.specification.AiUsageSpec.GLOBAL_UID;

/** Race-safe daily token quota reservation, settlement, inspection and reset. */
@Service
public class AiUsageService {

    private static final Logger LOG = LoggerFactory.getLogger(AiUsageService.class);

    /** Upper bound on the admin listing page size, so a caller cannot force an unbounded scan. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Share of the model window at which one turn's prompt is worth a warning. */
    private static final double WINDOW_WARN_RATIO = 0.9;

    private final AiDailyUsageRepository usageRepository;
    private final AiAdminSettingsService settings;
    private final TransactionTemplate transactions;
    private final long maxOutputAiUsageReservation;
    private final int modelWindowTokens;

    public AiUsageService(AiDailyUsageRepository usageRepository, AiAdminSettingsService settings,
            PlatformTransactionManager transactionManager,
            @Value("${spring.ai.openai.chat.max-tokens:4096}") long maxOutputAiUsageReservation,
            @Value("${app.ai.context.model-window-tokens:128000}") int modelWindowTokens) {
        this.usageRepository = usageRepository;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxOutputAiUsageReservation = maxOutputAiUsageReservation > 0 ? maxOutputAiUsageReservation : 4096L;
        this.modelWindowTokens = modelWindowTokens > 0 ? modelWindowTokens : 128_000;
    }

    /**
     * Reserves the estimated prompt plus maximum configured answer before calling the provider.
     *
     * <p>Admission is a <b>soft</b> gate: a turn is refused only once the day's budget is actually
     * spent ({@code used >= limit}), not when this turn's reservation would merely push the counter
     * past the ceiling. Reserving the full prompt plus {@code max-tokens} before every call used to
     * reject a turn that still had budget left, so a user could be told "额度已用完" while the
     * answer they had already paid for never arrived. An admitted turn now always runs to
     * completion; the overshoot is bounded by one turn ({@code estimate + max-tokens}).</p>
     *
     * <p>The reservation still guards concurrency: the first turn pushes {@code used} past the
     * ceiling, so a second turn in flight is refused until the first one settles.</p>
     */
    @Transactional
    public AiUsageReservation reserve(long uid, int estimatedInputTokens) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        AiSettings runtime = settings.get();
        long limit = runtime.dailyTokenLimit();
        long estimate = Math.max(1, estimatedInputTokens);
        long reservation = Math.addExact(estimate, maxOutputAiUsageReservation);

        // Lock order is always global-then-user, matching resetToday()'s uid-ascending order, so a
        // concurrent reset and a concurrent turn can never close a lock cycle.
        boolean global = runtime.globalQuotaActive();
        AiDailyUsageEntity globalRow = null;
        if (global) {
            globalRow = rowForUpdate(new AiDailyUsageId(GLOBAL_UID, date));
            long globalUsed = globalRow.getInputTokens() + globalRow.getOutputTokens()
                    + globalRow.getReservedTokens();
            if (globalUsed >= runtime.globalLimit()) {
                throw AiQuotaExceededException.global(runtime.globalLimit(), globalUsed);
            }
        }

        AiDailyUsageEntity row = rowForUpdate(new AiDailyUsageId(uid, date));
        long used = row.getInputTokens() + row.getOutputTokens() + row.getReservedTokens();
        if (used >= limit) {
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
        return new AiUsageReservation(uid, date, reservation, estimate, global, limit, used);
    }

    /** Starts accounting for a model call after its prompt has been assembled. */
    @Transactional
    public AiUsageTracker start(long uid, int estimatedInputTokens) {
        return new Session(this, reserve(uid, estimatedInputTokens), Math.max(0, estimatedInputTokens));
    }

    /** Settles a reservation with provider usage or a marked character-based estimate. */
    public void settle(AiUsageReservation reservation, AiTokenUsage usage) {
        transactions.executeWithoutResult(status -> settleInTransaction(reservation, usage));
    }

    /**
     * Commits usage measured so far without releasing the hold.
     *
     * <p>This is the point of the per-round flush: a turn that runs several model rounds would
     * otherwise stay invisible until it settles, so a second turn admitted during that window would
     * be checked against a budget that is already spent. Committing each round keeps the visible
     * total within one round of the truth, and leaves the hold untouched so the reservation still
     * guards the unfinished part.</p>
     *
     * @param reservation hold of the turn being flushed
     * @param inputTokens prompt tokens measured since the previous flush
     * @param outputTokens completion tokens measured since the previous flush
     */
    public void flushPartial(AiUsageReservation reservation, long inputTokens, long outputTokens) {
        if (inputTokens <= 0 && outputTokens <= 0) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            // Global first, then the user row: same lock order as reserve() and settle().
            if (reservation.global()) {
                applyDelta(GLOBAL_UID, reservation.date(), inputTokens, outputTokens);
            }
            applyDelta(reservation.uid(), reservation.date(), inputTokens, outputTokens);
        });
    }

    /**
     * Reads the live day for one turn: everything spent by everybody else, so a guard can decide
     * from numbers no older than the last flush instead of the snapshot taken at admission.
     *
     * <p>Reads the row without a lock on purpose — this feeds a best-effort guard in the middle of a
     * stream, and a slightly stale number only means the turn wraps up one round earlier or later.</p>
     *
     * @param reservation this turn's hold
     * @param flushedTokens how much of this turn was already written by {@link #flushPartial}
     * @param spentThisTurn tokens this turn measured but has not flushed yet
     * @return the day's remaining budget for this turn
     */
    public long remainingBudgetTokens(AiUsageReservation reservation, long flushedTokens,
            long spentThisTurn) {
        AiDailyUsageEntity row = usageRepository
                .findByIdUidAndIdUsageDate(reservation.uid(), reservation.date())
                .orElse(null);
        if (row == null) {
            return reservation.limit() - spentThisTurn;
        }
        long used = row.getInputTokens() + row.getOutputTokens() + row.getReservedTokens();
        // The row still carries this turn's hold and its flushed part; neither is "someone else's".
        long others = Math.max(0, used - reservation.reservedTokens() - flushedTokens);
        return reservation.limit() - others - spentThisTurn;
    }

    private void applyDelta(long uid, LocalDate date, long inputTokens, long outputTokens) {
        AiDailyUsageEntity row = rowForUpdate(new AiDailyUsageId(uid, date));
        row.setInputTokens(row.getInputTokens() + Math.max(0, inputTokens));
        row.setOutputTokens(row.getOutputTokens() + Math.max(0, outputTokens));
        usageRepository.save(row);
    }

    /** Releases a reservation when no model request completed. */
    @Transactional
    public void release(AiUsageReservation reservation) {
        settle(reservation, new AiTokenUsage(0, 0, false));
    }

    /** @return the authenticated user's UTC-day usage, including an empty snapshot */
    @Transactional(readOnly = true)
    public AiDailyUsage forUser(long uid) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        AiDailyUsageEntity row = usageRepository.findById(new AiDailyUsageId(uid, date)).orElse(null);
        return row == null
                ? new AiDailyUsage(date, 0, 0, 0, 0, 0, settings.get().dailyTokenLimit(), 0)
                : withLimit(toUsage(row), settings.get().dailyTokenLimit());
    }

    /** @return all recorded users' UTC-day usage, largest consumers first */
    @Transactional(readOnly = true)
    public List<AiUserUsage> allUsersToday() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        long limit = settings.get().dailyTokenLimit();
        return usageRepository.findByIdUsageDate(date).stream()
                .filter(row -> row.getId().getUid() != GLOBAL_UID)
                .sorted(Comparator.comparingLong((AiDailyUsageEntity row) ->
                                row.getInputTokens() + row.getOutputTokens()).reversed()
                        .thenComparing(row -> row.getId().getUid()))
                .map(row -> toAiUserUsage(row, date, limit))
                .toList();
    }

    /**
     * Paged, filtered administrator listing of one UTC day's per-user usage.
     *
     * <p>Filtering and ordering happen in SQL: {@code keyword} matches the {@code biz_user} mirror
     * (username / nickname / uid) through a correlated subquery, {@code onlyExceeded} compares the
     * stored counters against the current daily limit, and the total is ordered through the
     * mapped {@code totalTokens} expression.</p>
     *
     * @param keyword optional username / nickname / uid substring
     * @param onlyExceeded keep only users at or above their daily token limit
     * @param sort {@code tokens} (default), {@code tokens_asc}, {@code requests} or {@code uid}
     * @param page zero-based page index
     * @param size page size, clamped to 1..100
     * @return one page of usage rows
     */
    @Transactional(readOnly = true)
    public Page<AiUserUsage> pageUsersToday(String keyword, boolean onlyExceeded, String sort, int page, int size) {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        long limit = settings.get().dailyTokenLimit();
        Pageable pageable = PageRequest.of(Math.max(0, page),
                Math.min(MAX_PAGE_SIZE, Math.max(1, size)), usageSort(sort));
        return usageRepository.findAll(AiUsageSpec.of(date, keyword, onlyExceeded, limit), pageable)
                .map(row -> toAiUserUsage(row, date, limit));
    }

    /** @return the requested ordering, always falling back to uid for a deterministic page */
    private static Sort usageSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "tokens_asc" -> Sort.by(Sort.Direction.ASC, "totalTokens").and(Sort.by("id.uid"));
            case "requests" -> Sort.by(Sort.Direction.DESC, "requestCount").and(Sort.by("id.uid"));
            case "uid" -> Sort.by(Sort.Direction.ASC, "id.uid");
            default -> Sort.by(Sort.Direction.DESC, "totalTokens").and(Sort.by("id.uid"));
        };
    }

    private static AiUserUsage toAiUserUsage(AiDailyUsageEntity row, LocalDate date, long limit) {
        return new AiUserUsage(row.getId().getUid(), date, row.getInputTokens(), row.getOutputTokens(),
                row.getReservedTokens(), row.getRequestCount(), row.getEstimatedRequests(), limit,
                row.getInputTokens() + row.getOutputTokens());
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
    public AiGlobalUsage globalToday() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        AiSettings runtime = settings.get();
        AiDailyUsageEntity row = usageRepository.findById(new AiDailyUsageId(GLOBAL_UID, date)).orElse(null);
        long input = row == null ? 0L : row.getInputTokens();
        long output = row == null ? 0L : row.getOutputTokens();
        long reserved = row == null ? 0L : row.getReservedTokens();
        int requests = row == null ? 0 : row.getRequestCount();
        long limit = runtime.globalLimit();
        boolean enabled = runtime.globalQuotaActive();
        boolean tripped = enabled && input + output + reserved >= limit;
        return new AiGlobalUsage(date, input, output, reserved, requests, limit, enabled, tripped);
    }

    /**
     * @param days window length (clamped to 1..366)
     * @return platform-wide totals per UTC day, oldest first; days without traffic are omitted
     */
    @Transactional(readOnly = true)
    public List<AiDailyTotal> dailyTotals(int days) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(Math.max(1, Math.min(366, days)) - 1L);
        return usageRepository.findByIdUidAndIdUsageDateBetween(GLOBAL_UID, from, to).stream()
                .map(row -> new AiDailyTotal(row.getId().getUsageDate(), row.getInputTokens(),
                        row.getOutputTokens(), row.getRequestCount(), row.getEstimatedRequests()))
                .sorted(Comparator.comparing(AiDailyTotal::date))
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

    private void settleInTransaction(AiUsageReservation reservation, AiTokenUsage usage) {
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

    /** One AI call's mutable accumulator; mutation is limited to its streaming lifecycle. */
    public static final class Session implements AiUsageTracker {

        private final AiUsageService owner;
        private final AiUsageReservation reservation;
        private final int estimatedInputTokens;
        private final AiTokenUsageMeter meter = new AiTokenUsageMeter();
        private final StringBuilder output = new StringBuilder();
        private long flushedInput;
        private long flushedOutput;
        private boolean settled;
        private boolean windowWarned;
        private AiTokenUsage result;

        private Session(AiUsageService owner, AiUsageReservation reservation, int estimatedInputTokens) {
            this.owner = owner;
            this.reservation = reservation;
            this.estimatedInputTokens = estimatedInputTokens;
        }

        /** Adds provider usage metadata from one stream chunk. */
        @Override
        public synchronized void record(Usage usage) {
            warnIfNearModelWindow(usage);
            meter.record(usage);
        }

        /**
         * Warns once per turn when the provider reports a prompt close to the model window.
         *
         * <p>The replay budget is decided by an estimate, so the estimator cannot notice that it is
         * wrong — provider usage is the only ground truth in the pipeline. This is the backstop for
         * that gap: a single warning per turn is enough, because a prompt that keeps growing would
         * otherwise log on every round. Nothing is corrected automatically; the fix is lowering
         * {@code history-window-ratio}, which is a deployment decision.</p>
         *
         * @param usage provider usage for one round, may be {@code null}
         */
        private void warnIfNearModelWindow(Usage usage) {
            if (windowWarned || usage == null) {
                return;
            }
            Integer prompt = usage.getPromptTokens();
            if (prompt == null || prompt <= 0) {
                return;
            }
            long threshold = Math.round(owner.modelWindowTokens * WINDOW_WARN_RATIO);
            if (prompt >= threshold) {
                windowWarned = true;
                LOG.warn("Prompt used {} of the {}-token model window; lower "
                        + "app.ai.context.history-window-ratio if this repeats", prompt,
                        owner.modelWindowTokens);
            }
        }

        /** Appends generated answer text for a fallback estimate if the provider omits usage. */
        @Override
        public synchronized void append(String text) {
            if (text != null && !text.isEmpty()) {
                output.append(text);
            }
        }

        /**
         * Writes the usage accumulated since the previous flush without releasing the hold.
         *
         * <p>Failures are swallowed: accounting must never break a running turn, and the amount that
         * could not be flushed is still settled at the end.</p>
         */
        @Override
        public synchronized void flush() {
            if (settled) {
                return;
            }
            AiTokenUsage snapshot = meter.snapshot(estimatedInputTokens, output.toString());
            // Monotonic in practice, but an estimated flush followed by real provider usage can
            // overshoot: clamp so the counters never move backwards and the day is never credited.
            long input = Math.max(0, snapshot.inputTokens() - flushedInput);
            long outputTokens = Math.max(0, snapshot.outputTokens() - flushedOutput);
            if (input == 0 && outputTokens == 0) {
                return;
            }
            try {
                owner.flushPartial(reservation, input, outputTokens);
                flushedInput += input;
                flushedOutput += outputTokens;
            } catch (RuntimeException ex) {
                LOG.warn("Failed to flush AI token usage for uid={} date={}", reservation.uid(),
                        reservation.date(), ex);
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p>Reads the live day instead of the admission snapshot, so usage committed by the other
         * turns of the same user counts against this one. When the read fails it falls back to the
         * snapshot: a guard that cannot see the database must not stop a turn that still has budget.
         * </p>
         */
        @Override
        public synchronized long remainingTokens() {
            if (settled) {
                return Long.MAX_VALUE;
            }
            long spent = spentTokens();
            try {
                return owner.remainingBudgetTokens(reservation, flushedInput + flushedOutput, spent);
            } catch (RuntimeException ex) {
                return reservation.limit() - reservation.usedBefore() - spent;
            }
        }

        /**
         * @return tokens this turn has measured so far: provider usage when available, otherwise the
         *         first-round prompt estimate plus the generated text
         */
        private long spentTokens() {
            return meter.hasReportedUsage()
                    ? meter.consumed()
                    : (long) estimatedInputTokens + AiTokenUsageMeter.estimateTokens(output.toString());
        }

        /** Settles actual usage once on success, failure, or stream cancellation. */
        @Override
        public synchronized AiTokenUsage finish() {
            if (!settled) {
                settled = true;
                result = meter.snapshot(estimatedInputTokens, output.toString());
                // Only the part that no flush has committed yet: settle() releases the hold and adds
                // this delta, so the day never counts the same tokens twice.
                AiTokenUsage delta = new AiTokenUsage(
                        Math.max(0, result.inputTokens() - flushedInput),
                        Math.max(0, result.outputTokens() - flushedOutput),
                        result.estimated());
                try {
                    owner.settle(reservation, delta);
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
