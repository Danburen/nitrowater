package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.ProblemEditorial;
import cn.nitrowater.server.infra.enums.EditorialStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link ProblemEditorial}. */
public interface ProblemEditorialRepository extends JpaRepository<ProblemEditorial, Long> {

    /**
     * Public listing: published editorials of a problem, pinned &amp; most-liked first
     * (LeetCode Solutions order).
     *
     * @param problemId owning problem id
     * @param status editorial status filter (typically {@link EditorialStatus#PUBLISHED})
     * @return published editorials in display order
     */
    List<ProblemEditorial> findByProblemIdAndStatusOrderByPinnedDescLikeCountDescPublishedAtDesc(
            Long problemId, EditorialStatus status);

    /**
     * Admin listing: every editorial of a problem regardless of status.
     *
     * @param problemId owning problem id
     * @return all editorials in display order
     */
    List<ProblemEditorial> findByProblemIdOrderByPinnedDescLikeCountDescPublishedAtDesc(Long problemId);

    /**
     * @param problemId owning problem id
     * @param status editorial status filter
     * @return number of editorials with that status
     */
    long countByProblemIdAndStatus(Long problemId, EditorialStatus status);

    /**
     * @param problemId owning problem id
     */
    void deleteByProblemId(Long problemId);
}
