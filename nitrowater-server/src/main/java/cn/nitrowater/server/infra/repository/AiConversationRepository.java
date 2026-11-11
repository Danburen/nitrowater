package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for {@link AiConversation}. */
public interface AiConversationRepository extends JpaRepository<AiConversation, Long>,
        JpaSpecificationExecutor<AiConversation> {

    /**
     * @param uuid external conversation id
     * @param uid owner uid; scoping by owner prevents cross-user access
     * @return the conversation when it belongs to that user
     */
    Optional<AiConversation> findByUuidAndUid(String uuid, Long uid);

    /**
     * @param uid owner uid
     * @param status lifecycle filter
     * @param cursor last seen {@code lastMessageAt} of the previous page, or {@code null} for the first page
     * @param limit page size
     * @return conversations ordered by most recent message first
     */
    List<AiConversation> findByUidAndStatusAndLastMessageAtLessThanOrderByLastMessageAtDesc(
            Long uid, ConversationStatus status, Instant cursor, Limit limit);

    /**
     * @param uid owner uid
     * @param status lifecycle filter
     * @param limit page size
     * @return the first page of conversations ordered by most recent message first
     */
    List<AiConversation> findByUidAndStatusOrderByLastMessageAtDesc(Long uid, ConversationStatus status, Limit limit);

    /**
     * @param uid owner uid
     * @return number of non-deleted conversations
     */
    long countByUidAndStatusNot(Long uid, ConversationStatus status);

    /**
     * Stores a freshly generated rolling summary, advancing the context boundary in the same
     * statement.
     *
     * <p>{@code compress_version} is the optimistic lock: a concurrent compaction that already
     * moved the boundary wins, and this one updates zero rows and is discarded. The update is
     * deliberately a bulk statement so a summarizer running outside a transaction cannot overwrite
     * a newer turn's state.</p>
     *
     * @param id conversation internal id
     * @param version expected {@code compress_version}
     * @param summary new summary text
     * @param boundary new {@code summary_upto_seq}
     * @param now update timestamp
     * @return number of rows updated ({@code 0} means another compaction won)
     */
    @Modifying
    @Transactional
    @Query("""
            update AiConversation c
               set c.summary = :summary,
                   c.summaryUptoSeq = :boundary,
                   c.compressVersion = c.compressVersion + 1,
                   c.updatedAt = :now
             where c.id = :id and c.compressVersion = :version
            """)
    int updateSummary(@Param("id") Long id, @Param("version") Integer version,
            @Param("summary") String summary, @Param("boundary") Integer boundary,
            @Param("now") Instant now);
}
