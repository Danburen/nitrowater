package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.ProblemSet;
import cn.nitrowater.server.infra.enums.ProblemSetVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link ProblemSet}. */
public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {

    /**
     * @param slug stable set slug
     * @return the set with that slug
     */
    Optional<ProblemSet> findBySlug(String slug);

    /**
     * @param slug candidate slug
     * @return whether a set with that slug already exists
     */
    boolean existsBySlug(String slug);

    /**
     * @return enabled sets ordered by sort order then id (stable listing order)
     */
    List<ProblemSet> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /**
     * @return every set ordered by sort order then id (admin listing)
     */
    List<ProblemSet> findAllByOrderBySortOrderAscIdAsc();

    /**
     * @return enabled curated (owner-less) sets, ordered (public official listing)
     */
    List<ProblemSet> findByOwnerUidIsNullAndEnabledTrueOrderBySortOrderAscIdAsc();

    /**
     * @param ownerUid owner uid
     * @return the owner's enabled sets, most recently updated first
     */
    List<ProblemSet> findByOwnerUidAndEnabledTrueOrderByUpdatedAtDescIdDesc(Long ownerUid);

    /**
     * @param visibility visibility to match (e.g. {@code PUBLIC})
     * @return enabled user-owned sets with that visibility, most recently updated first (题单广场)
     */
    List<ProblemSet> findByOwnerUidIsNotNullAndVisibilityAndEnabledTrueOrderByUpdatedAtDescIdDesc(
            ProblemSetVisibility visibility);
}
