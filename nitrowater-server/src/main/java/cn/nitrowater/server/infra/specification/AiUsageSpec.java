package cn.nitrowater.server.infra.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.server.infra.entity.AiDailyUsageEntity;
import cn.nitrowater.server.infra.entity.BizUser;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

/**
 * JPA filters for the daily token-usage listing.
 */
public final class AiUsageSpec {

    /**
     * Sentinel uid for the all-users counter. Real uids start at 1, and this row reuses the exact
     * same reservation/settlement path as a per-user row, so the global breaker needs no second
     * data structure.
     */
    public static final long GLOBAL_UID = 0L;

    private AiUsageSpec() {
    }

    /**
     * Matches the per-user usage rows of one day, optionally over the daily quota or owned by a
     * user matching the keyword. The global sentinel row is always excluded.
     *
     * @param date usage date (UTC)
     * @param keyword optional username / nickname / uid substring
     * @param onlyExceeded whether to keep only rows at or over the quota
     * @param limit daily token quota per user; {@code <= 0} means unlimited
     * @return the combined specification
     */
    public static Specification<AiDailyUsageEntity> of(LocalDate date, String keyword,
            boolean onlyExceeded, long limit) {
        SpecificationBuilder<AiDailyUsageEntity> builder = SpecificationBuilder.<AiDailyUsageEntity>builder()
                .eq("id.usageDate", date)
                .notEqual("id.uid", GLOBAL_UID);
        if (StringUtil.isNotBlank(keyword)) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            builder.where((root, query, cb) -> {
                Subquery<Long> match = query.subquery(Long.class);
                Root<BizUser> user = match.from(BizUser.class);
                match.select(user.get("uid")).where(cb.and(
                        cb.equal(user.get("uid"), root.get("id").get("uid")),
                        cb.or(
                                cb.like(cb.lower(user.get("username")), like),
                                cb.like(cb.lower(user.get("nickname")), like),
                                cb.like(cb.lower(user.get("uid").as(String.class)), like))));
                return cb.exists(match);
            });
        }
        if (onlyExceeded) {
            if (limit <= 0) {
                // Unlimited quota: nobody can be over it.
                return builder.where((root, query, cb) -> cb.disjunction()).build();
            }
            builder.where((root, query, cb) -> cb.greaterThanOrEqualTo(
                    cb.sum(root.get("inputTokens"), root.get("outputTokens")).as(Long.class), limit));
        }
        return builder.build();
    }
}
