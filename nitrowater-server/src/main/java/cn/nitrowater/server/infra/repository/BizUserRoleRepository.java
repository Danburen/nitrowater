package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.BizUserRole;
import cn.nitrowater.server.infra.entity.BizUserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link BizUserRole} (user↔role mapping). */
public interface BizUserRoleRepository extends JpaRepository<BizUserRole, BizUserRoleId> {

    /**
     * @param userId business user id
     * @return the role mappings of that user
     */
    List<BizUserRole> findByIdUserId(Long userId);
}
