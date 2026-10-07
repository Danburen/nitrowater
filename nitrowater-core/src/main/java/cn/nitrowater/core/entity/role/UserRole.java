package cn.nitrowater.core.entity.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** User-to-role mapping (table {@code user_role}). */
@Getter
@Setter
@Entity
@Table(name = "user_role")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    @Column(name = "granted_at")
    @CreationTimestamp
    private Instant grantedAt;

    /** Read-only view of the bound role (role_id is managed by the id fields). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;
}
