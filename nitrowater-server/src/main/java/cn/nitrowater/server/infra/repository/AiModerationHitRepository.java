package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiModerationHit;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

/**
 * Persistence for {@link AiModerationHit}.
 *
 * <p>{@link JpaSpecificationExecutor} carries the admin review filters: direction, action and the
 * {@code beforeId} cursor are all optional, and building them as a {@code Specification} keeps the
 * null handling in the query layer instead of requiring one derived method per combination.</p>
 */
public interface AiModerationHitRepository
        extends JpaRepository<AiModerationHit, Long>, JpaSpecificationExecutor<AiModerationHit> {

    /**
     * @param limit page size
     * @return the newest hits first
     */
    List<AiModerationHit> findAllByOrderByCreatedAtDesc(Limit limit);

    /**
     * @param since lower bound (exclusive)
     * @return number of hits newer than the given instant
     */
    long countByCreatedAtAfter(Instant since);

    /**
     * Hard-deletes hits older than the cutoff, bounding the audit trail by the retention window.
     *
     * @param cutoff exclusive upper bound
     * @return how many rows were deleted
     */
    long deleteByCreatedAtBefore(Instant cutoff);
}
