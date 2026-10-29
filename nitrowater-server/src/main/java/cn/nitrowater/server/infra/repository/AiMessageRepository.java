package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link AiMessage}. */
public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    /**
     * @param conversationId internal conversation id
     * @param seq upper bound (exclusive) for paging upwards; use a large value for the newest page
     * @param limit page size
     * @return messages ordered by descending seq
     */
    List<AiMessage> findByConversationIdAndSeqLessThanOrderBySeqDesc(Long conversationId, Integer seq, Limit limit);

    /**
     * @param conversationId internal conversation id
     * @return the message with the highest seq, if any
     */
    Optional<AiMessage> findFirstByConversationIdOrderBySeqDesc(Long conversationId);

    /**
     * @param conversationId internal conversation id
     * @return the most recent messages, newest first (used to rebuild prompt history)
     */
    List<AiMessage> findByConversationIdOrderBySeqDesc(Long conversationId, Limit limit);

    /**
     * @param conversationId internal conversation id
     * @param seq exclusive lower bound; messages at or below the context boundary are already
     *            represented by the rolling summary (or were dropped by {@code /clear})
     * @param limit page size
     * @return messages ordered by descending seq
     */
    List<AiMessage> findByConversationIdAndSeqGreaterThanOrderBySeqDesc(Long conversationId, Integer seq, Limit limit);

    /**
     * @param conversationId internal conversation id
     * @param seq exclusive lower bound
     * @return number of messages after the given seq
     */
    long countByConversationIdAndSeqGreaterThan(Long conversationId, Integer seq);

    /**
     * @param conversationId internal conversation id
     * @return number of stored messages
     */
    long countByConversationId(Long conversationId);
}
