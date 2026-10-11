package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.UserPointLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link UserPointLog} (point ledger). */
public interface UserPointLogRepository extends JpaRepository<UserPointLog, Long> {

    /**
     * @param userId business user id
     * @return that user's ledger entries, newest first
     */
    List<UserPointLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
