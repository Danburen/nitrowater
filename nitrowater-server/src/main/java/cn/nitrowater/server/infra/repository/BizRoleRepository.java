package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.BizRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link BizRole}. */
public interface BizRoleRepository extends JpaRepository<BizRole, Long> {

    /**
     * @param code role code, e.g. {@code PROBLEM_ADMIN}
     * @return the role with that code
     */
    Optional<BizRole> findByCode(String code);
}
