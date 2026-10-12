package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.ProblemSetItem;
import cn.nitrowater.server.infra.entity.ProblemSetItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link ProblemSetItem}. */
public interface ProblemSetItemRepository extends JpaRepository<ProblemSetItem, ProblemSetItemId> {

    /**
     * @param problemId owning problem id
     * @return the problem's set links
     */
    List<ProblemSetItem> findByIdProblemId(Long problemId);

    /**
     * @param setId owning set id
     * @return the set's items ordered by {@code ord} then problem id
     */
    List<ProblemSetItem> findByIdSetIdOrderByOrdAscIdProblemIdAsc(Long setId);

    /**
     * @param setId owning set id
     * @return number of problems in the set
     */
    long countByIdSetId(Long setId);

    /**
     * @param problemId owning problem id
     */
    void deleteByIdProblemId(Long problemId);

    /**
     * @param setId owning set id
     */
    void deleteByIdSetId(Long setId);
}
