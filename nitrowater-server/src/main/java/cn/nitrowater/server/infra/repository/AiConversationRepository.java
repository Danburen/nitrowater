package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for {@link AiConversation}. */
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

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
}
