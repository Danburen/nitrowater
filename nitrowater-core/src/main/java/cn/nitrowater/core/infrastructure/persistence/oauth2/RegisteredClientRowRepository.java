package cn.nitrowater.core.infrastructure.persistence.oauth2;

import cn.nitrowater.core.domain.oauth2.RegisteredClientRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JPA queries over the vendor-owned {@code oauth2_registered_client} table.
 *
 * <p>Only the admin console reads through this repository; Spring Authorization Server keeps using
 * {@code JdbcRegisteredClientRepository} for protocol reads and every write.</p>
 */
public interface RegisteredClientRowRepository
        extends JpaRepository<RegisteredClientRow, String>, JpaSpecificationExecutor<RegisteredClientRow> {
}
