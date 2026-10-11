package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.BizUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link BizUser} (business-side user mirror). */
public interface BizUserRepository extends JpaRepository<BizUser, Long> {

    /**
     * @param uid account uid
     * @return the mirror row for that account user
     */
    Optional<BizUser> findByUid(Long uid);
}
