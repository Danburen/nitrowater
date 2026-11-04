package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiDailyUsageEntity;
import cn.nitrowater.server.infra.entity.AiDailyUsageId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence for per-user (and the global sentinel) per-UTC-day AI usage. */
public interface AiDailyUsageRepository extends JpaRepository<AiDailyUsageEntity, AiDailyUsageId>,
        JpaSpecificationExecutor<AiDailyUsageEntity> {

    /**
     * Loads an existing counter while holding its database row lock for quota settlement.
     *
     * <p>A derived query keeps the composite-key predicate explicit; passing an embeddable as a
     * single JPQL parameter is provider-dependent and unnecessary here.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiDailyUsageEntity> findByIdUidAndIdUsageDate(Long uid, LocalDate usageDate);

    /** @param usageDate UTC day
     *  @return all counters recorded for that day */
    List<AiDailyUsageEntity> findByIdUsageDate(LocalDate usageDate);

    /**
     * @param uid counter owner ({@code 0} = the global sentinel row)
     * @param from inclusive start day
     * @param to inclusive end day
     * @return the owner's counters inside the window, unordered
     */
    List<AiDailyUsageEntity> findByIdUidAndIdUsageDateBetween(Long uid, LocalDate from, LocalDate to);

    /**
     * Deletes per-user day rows older than the cutoff (retention).
     *
     * @param cutoff exclusive upper bound
     * @param globalUid sentinel uid to spare
     * @return deleted rows
     */
    @Modifying
    @Query("delete from AiDailyUsageEntity e where e.id.usageDate < :cutoff and e.id.uid <> :globalUid")
    int deleteUserRowsBefore(@Param("cutoff") LocalDate cutoff, @Param("globalUid") long globalUid);

    /**
     * Deletes global sentinel rows older than the cutoff. Kept longer than per-user rows so the
     * platform-wide series survives long after individual users' detail has been pruned.
     *
     * @param cutoff exclusive upper bound
     * @param globalUid sentinel uid
     * @return deleted rows
     */
    @Modifying
    @Query("delete from AiDailyUsageEntity e where e.id.usageDate < :cutoff and e.id.uid = :globalUid")
    int deleteGlobalRowsBefore(@Param("cutoff") LocalDate cutoff, @Param("globalUid") long globalUid);
}
