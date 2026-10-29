package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiUserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AiUserProfile}. */
public interface AiUserProfileRepository extends JpaRepository<AiUserProfile, Long> {
}
