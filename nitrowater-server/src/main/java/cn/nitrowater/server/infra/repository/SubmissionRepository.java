package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link Submission}. */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * @param userId business user id
     * @return that user's submissions, newest first
     */
    List<Submission> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * @param problemId problem id
     * @return submissions for that problem, newest first
     */
    List<Submission> findByProblemIdOrderByCreatedAtDesc(Long problemId);
}
