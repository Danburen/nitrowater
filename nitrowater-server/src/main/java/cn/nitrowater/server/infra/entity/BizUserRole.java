package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Maps a business user to an application role.
 *
 * <p>Composite PK {@code (user_id, role_id)}; the FKs are enforced in the DB
 * ({@code ON DELETE CASCADE}).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "biz_user_role")
public class BizUserRole {

    @EmbeddedId
    private BizUserRoleId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
