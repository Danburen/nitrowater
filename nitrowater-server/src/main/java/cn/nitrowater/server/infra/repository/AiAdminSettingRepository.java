package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiAdminSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence for the singleton AI administrator settings row. */
public interface AiAdminSettingRepository extends JpaRepository<AiAdminSetting, Integer> {

    /** Locks the singleton row so quota admission is serialized across application instances. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select setting from AiAdminSetting setting where setting.id = :id")
    Optional<AiAdminSetting> findForUpdate(@Param("id") Integer id);
}
