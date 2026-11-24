package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.InstanceCommandAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link InstanceCommandAudit} (metadata only). */
public interface InstanceCommandAuditRepository extends JpaRepository<InstanceCommandAudit, Long> {

    /**
     * @param commandId stable command id
     * @return the audit row, if any
     */
    Optional<InstanceCommandAudit> findByCommandId(String commandId);
}
