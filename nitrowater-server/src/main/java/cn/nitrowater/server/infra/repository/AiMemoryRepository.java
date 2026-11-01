package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiMemory;
import cn.nitrowater.server.infra.enums.MemoryKind;
import cn.nitrowater.server.infra.enums.MemoryStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence for {@link AiMemory}. */
public interface AiMemoryRepository extends JpaRepository<AiMemory, Long> {

    /**
     * @param uuid memory uuid
     * @param uid owner uid
     * @return the memory when it belongs to that user
     */
    Optional<AiMemory> findByUuidAndUid(String uuid, Long uid);

    /**
     * @param uid owner uid
     * @param kind memory category
     * @param status lifecycle filter
     * @param limit page size
     * @return the most recently changed entries of that kind
     */
    List<AiMemory> findByUidAndKindAndStatusOrderByUpdatedAtDesc(
            Long uid, MemoryKind kind, MemoryStatus status, Limit limit);

    /**
     * @param uid owner uid
     * @param status lifecycle filter
     * @param confidence minimum confidence
     * @param limit page size
     * @return injectable memories, most recently used first
     */
    List<AiMemory> findByUidAndStatusAndConfidenceGreaterThanEqualOrderByLastUsedAtDescUpdatedAtDesc(
            Long uid, MemoryStatus status, Short confidence, Limit limit);

    /**
     * @param uid owner uid
     * @param status lifecycle filter
     * @param limit page size
     * @return most recently changed entries
     */
    List<AiMemory> findByUidAndStatusOrderByUpdatedAtDesc(Long uid, MemoryStatus status, Limit limit);

    /**
     * @param uid owner uid
     * @param status lifecycle filter
     * @return the least recently used entry, used to enforce the per-user cap
     */
    Optional<AiMemory> findFirstByUidAndStatusOrderByLastUsedAtAscUpdatedAtAsc(Long uid, MemoryStatus status);

    /**
     * @param uid owner uid
     * @param status lifecycle filter
     * @return number of memories in that state
     */
    long countByUidAndStatus(Long uid, MemoryStatus status);

    /**
     * Marks every active memory of one user as deleted.
     *
     * @param uid owner uid
     * @param active current status filter
     * @param deleted target status
     * @param now update instant
     * @return number of affected rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiMemory m
               set m.status = :deleted, m.updatedAt = :now
             where m.uid = :uid and m.status = :active
            """)
    int deleteAllByUid(@Param("uid") Long uid, @Param("active") MemoryStatus active,
            @Param("deleted") MemoryStatus deleted, @Param("now") Instant now);

    /**
     * Expires active memories that have not been used or changed since the cutoff.
     *
     * @param active current status filter
     * @param expired target status
     * @param cutoff last-used/updated cutoff
     * @param now update instant
     * @return number of affected rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiMemory m
               set m.status = :expired, m.updatedAt = :now
             where m.status = :active
               and m.updatedAt < :cutoff
               and (m.lastUsedAt is null or m.lastUsedAt < :cutoff)
            """)
    int expireStale(@Param("active") MemoryStatus active, @Param("expired") MemoryStatus expired,
            @Param("cutoff") Instant cutoff, @Param("now") Instant now);

    /**
     * Physically removes old soft-deleted/expired memories.
     *
     * @param statuses terminal statuses eligible for purge
     * @param cutoff update cutoff
     * @return number of removed rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiMemory m where m.status in :statuses and m.updatedAt < :cutoff")
    int purgeOlderThan(@Param("statuses") Collection<MemoryStatus> statuses,
            @Param("cutoff") Instant cutoff);
}
