package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiMemory;
import cn.nitrowater.server.infra.enums.MemoryStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
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
     * @param status lifecycle filter
     * @param confidence minimum confidence
     * @param notUsedSince only return entries unused since this instant (LRU ordering)
     * @param limit page size
     * @return injectable memories, least recently used first
     */
    List<AiMemory> findByUidAndStatusAndConfidenceGreaterThanEqualAndLastUsedAtBeforeOrderByLastUsedAtAsc(
            Long uid, MemoryStatus status, Short confidence, Instant notUsedSince, Limit limit);

    /**
     * @param uid owner uid
     * @param status lifecycle filter
     * @return number of memories in that state
     */
    long countByUidAndStatus(Long uid, MemoryStatus status);

    /**
     * @param sourceConversationUuid conversation uuid
     * @return memories derived from that conversation
     */
    List<AiMemory> findBySourceConversationUuid(String sourceConversationUuid);
}
