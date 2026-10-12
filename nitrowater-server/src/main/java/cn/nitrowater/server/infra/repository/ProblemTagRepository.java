package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.ProblemTag;
import cn.nitrowater.server.infra.entity.ProblemTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link ProblemTag}. */
public interface ProblemTagRepository extends JpaRepository<ProblemTag, ProblemTagId> {

    /**
     * @param problemId owning problem id
     * @return the problem's tag links
     */
    List<ProblemTag> findByIdProblemId(Long problemId);

    /**
     * @param problemId owning problem id
     */
    void deleteByIdProblemId(Long problemId);
}
