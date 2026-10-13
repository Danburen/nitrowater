package cn.nitrowater.core.infrastructure.persistence.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.nitrowater.core.domain.role.UserRole;
import cn.nitrowater.core.domain.role.UserRoleId;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /** Role codes bound to the given uid, ordered by code. */
    @Query("SELECT r.code FROM UserRole ur JOIN ur.role r WHERE ur.uid = :uid ORDER BY r.code")
    List<String> findRoleCodesByUid(@Param("uid") Long uid);

    /** All role bindings of a user (role eagerly fetched for code/name access). */
    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role r WHERE ur.uid = :uid ORDER BY r.code")
    List<UserRole> findByUidWithRole(@Param("uid") Long uid);

    /**
     * Role bindings for a batch of users, role eagerly fetched, so a list page needs a
     * single extra query instead of one per row (avoids N+1).
     */
    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role r WHERE ur.uid IN :uids")
    List<UserRole> findByUidInWithRole(@Param("uids") Collection<Long> uids);

    /** Number of users currently bound to the given role code (in-use / last-super-admin guards). */
    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.code = :code")
    long countByRoleCode(@Param("code") String code);

    /** Number of users holding the given role id (delete guard). */
    long countByRoleId(Long roleId);

    /** Removes every role binding of a user (used before re-granting a new set). */
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.uid = :uid")
    int deleteByUid(@Param("uid") Long uid);
}
