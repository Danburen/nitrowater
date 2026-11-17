package cn.nitrowater.core.infrastructure.persistence.user;

import cn.nitrowater.core.infrastructure.domain.user.AccountAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA access to the project-owned {@code account_audit_log} table.
 *
 * <p>Writes go through the entity so the audit trail follows the same mapping rules as the rest of
 * the schema (instant columns, JSON columns) instead of hand-written SQL.</p>
 */
public interface AccountAuditLogRepository extends JpaRepository<AccountAuditLog, Long> {
}
