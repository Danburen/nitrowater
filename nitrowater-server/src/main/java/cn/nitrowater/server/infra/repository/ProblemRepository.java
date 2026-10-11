package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link Problem}. */
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    /**
     * @param slug stable problem slug
     * @return the problem with that slug
     */
    Optional<Problem> findBySlug(String slug);

    /**
     * @param slug candidate slug
     * @return whether a problem with that slug already exists
     */
    boolean existsBySlug(String slug);
}
