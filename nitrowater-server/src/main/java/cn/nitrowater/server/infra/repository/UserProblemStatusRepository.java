package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.UserProblemStatus;
import cn.nitrowater.server.infra.entity.UserProblemStatusId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link UserProblemStatus} (per-user problem aggregate). */
public interface UserProblemStatusRepository extends JpaRepository<UserProblemStatus, UserProblemStatusId> {

    /**
     * @param userId business user id
     * @return the aggregate rows of that user
     */
    List<UserProblemStatus> findByIdUserId(Long userId);
}
