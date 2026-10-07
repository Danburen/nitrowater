package cn.nitrowater.core.infrastructure.persistence.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.nitrowater.core.entity.role.UserRole;
import cn.nitrowater.core.entity.role.UserRoleId;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /** Role codes bound to the given uid, ordered by code. */
    @Query("SELECT r.code FROM UserRole ur JOIN ur.role r WHERE ur.uid = :uid ORDER BY r.code")
    List<String> findRoleCodesByUid(@Param("uid") Long uid);
}
