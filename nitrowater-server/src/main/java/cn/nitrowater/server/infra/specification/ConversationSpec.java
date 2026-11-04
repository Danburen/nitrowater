package cn.nitrowater.server.infra.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA filters for the conversation listing.
 */
public final class ConversationSpec {

    private ConversationSpec() {
    }

    /**
     * Matches one user's conversations by lifecycle status and title keyword.
     *
     * @param uid owner uid
     * @param status effective lifecycle status (the caller resolves the default)
     * @param keyword optional title substring
     * @return the combined specification
     */
    public static Specification<AiConversation> of(long uid, ConversationStatus status, String keyword) {
        return SpecificationBuilder.<AiConversation>builder()
                .eq("uid", uid)
                .eq("status", status)
                .likeIgnoreCase("title", keyword)
                .build();
    }
}
