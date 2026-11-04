package cn.nitrowater.server.infra.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.server.infra.entity.AiModerationHit;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

/**
 * JPA filters for the moderation-hit review listing.
 */
public final class AiModerationSpec {

    private AiModerationSpec() {
    }

    /**
     * Matches moderation hits by direction, action, time window and owner keyword.
     *
     * @param direction optional direction filter
     * @param action optional action filter
     * @param keyword optional username / nickname / uid substring of the hit owner
     * @param from inclusive lower bound on {@code createdAt}, or {@code null}
     * @param to exclusive upper bound on {@code createdAt}, or {@code null}
     * @return the combined specification
     */
    public static Specification<AiModerationHit> of(ModerationDirection direction,
            ModerationAction action, String keyword, Instant from, Instant to) {
        SpecificationBuilder<AiModerationHit> builder = SpecificationBuilder.<AiModerationHit>builder()
                .eq("direction", direction)
                .eq("action", action)
                .greaterThanOrEqualTo("createdAt", from)
                .lessThan("createdAt", to);
        if (StringUtil.isNotBlank(keyword)) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            builder.where((root, query, cb) -> {
                Subquery<Long> match = query.subquery(Long.class);
                Root<BizUser> user = match.from(BizUser.class);
                match.select(user.get("uid")).where(cb.and(
                        cb.equal(user.get("uid"), root.get("uid")),
                        cb.or(
                                cb.like(cb.lower(user.get("username")), like),
                                cb.like(cb.lower(user.get("nickname")), like),
                                cb.like(cb.lower(user.get("uid").as(String.class)), like))));
                return cb.exists(match);
            });
        }
        return builder.build();
    }
}
