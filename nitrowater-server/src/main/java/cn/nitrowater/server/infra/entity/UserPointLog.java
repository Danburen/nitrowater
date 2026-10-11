package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.PointReason;
import cn.nitrowater.server.infra.enums.PointRefType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Point ledger entry.
 *
 * <p>Unique {@code (user_id, reason, ref_type, ref_id)} makes first-AC awards idempotent.
 * {@code balance_after} is a redundant snapshot for reconciliation.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "user_point_log")
public class UserPointLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Signed delta. */
    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Column(name = "reason", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private PointReason reason;

    @Column(name = "ref_type", columnDefinition = "tinyint UNSIGNED")
    private PointRefType refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
