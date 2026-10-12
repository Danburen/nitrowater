package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.ProblemSet;
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
}
