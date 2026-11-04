package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiSkillEntity;
import cn.nitrowater.server.infra.repository.AiSkillRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Managed skills; the built-in ones live on the classpath and are never persisted. */
public interface AiSkillRepository extends JpaRepository<AiSkillEntity, Long> {

    /** @param name skill name (case-insensitive) @return the managed row, if any */
    Optional<AiSkillEntity> findByNameIgnoreCase(String name);

    /** @param name skill name (case-insensitive) @return whether a managed row exists */
    boolean existsByNameIgnoreCase(String name);

    /** @return every managed row, ordered by name (the agent's read path) */
    List<AiSkillEntity> findAllByOrderByNameAsc();
}
