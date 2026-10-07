package cn.nitrowater.core.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.nitrowater.core.entity.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * User credential repository (SSO scope).
 * <p>Copied and trimmed from waterfun: avatar/Resource/BriefDO/Counter JPQL removed.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :passwordHash WHERE u.uid = :userUid")
    int updatePassword(@Param("userUid") Long userUid, @Param("passwordHash") String passwordHash);

    List<User> getUsersByUid(Long id);

    Optional<User> findUserByUid(Long uid);

    boolean deleteUserByUid(Long uid);

    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = :lastActiveAt WHERE u.uid = :uid")
    int updateLastActiveAt(@Param("uid") Long uid, @Param("lastActiveAt") Instant lastActiveAt);

    @Query("SELECT u.createdAt FROM User u WHERE u.uid = :uid")
    Instant getUserCreatedAtByUid(@Param("uid") Long uid);
}
