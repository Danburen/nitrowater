package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiDailyUsageEntity;
import cn.nitrowater.server.infra.entity.AiDailyUsageId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence for per-user, per-UTC-day AI usage. */
public interface AiDailyUsageRepository extends JpaRepository<AiDailyUsageEntity, AiDailyUsageId> {

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
}
