package cn.nitrowater.server.infra.entity;

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
 * A registered VeloChatX instance.
 *
 * <p>Identity is the {@code (keyId, instanceId)} pair: the key fixes the owner, the UUID fixes the
 * machine. A row with a non-null {@link #removedAt} is soft-deleted and no longer consumes a slot,
 * so the machine can be re-registered later without colliding with the unique key.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "instance_registry")
public class InstanceRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Logical ref to instance_credential.key_id. */
    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    /** V-generated UUID identifying the machine (stable across key rotation / restarts). */
    @Column(name = "instance_id", nullable = false, length = 128)
    private String instanceId;

    /** Display name generated on first bind, editable by the user; unique per owner. */
    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /** Logical ref to biz_user.uid / account.user.uid. */
    @Column(name = "owner_uid")
    private Long ownerUid;

    /** Last reported process UUID, for clone detection in the admin console. */
    @Column(name = "boot_id", length = 64)
    private String bootId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    /** Soft-delete marker; non-null means the slot has been released. */
    @Column(name = "removed_at")
    private Instant removedAt;
}
