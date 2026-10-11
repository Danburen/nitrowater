package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.ProblemTestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link ProblemTestCase}. */
public interface ProblemTestCaseRepository extends JpaRepository<ProblemTestCase, Long> {

    /**
     * @param problemId problem id
     * @return the cases of that problem, in index order
     */
    List<ProblemTestCase> findByProblemIdOrderByIdxAsc(Long problemId);
}
