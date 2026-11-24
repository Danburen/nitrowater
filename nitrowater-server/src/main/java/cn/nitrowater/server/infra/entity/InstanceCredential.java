package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.InstanceCredentialStatus;
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
 * A VeloChatX instance api-key.
 *
 * <p>Only the SHA-256 hash of the plaintext key is stored — the key itself is shown once at
 * issuance and never persisted. The key is <b>user-level</b>: it fixes the owner via {@link #ownerUid}
 * but is not bound to any instance id. One key may serve up to {@code MAX_INSTANCES_PER_KEY}
 * instances, each registered in {@link InstanceRegistry}.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "instance_credential")
public class InstanceCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Display id (kid), used by V in logs and by the admin console. */
    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    /** SHA-256 hex of the plaintext api-key; the authentication lookup key. */
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    /** {@code nw_live_} plus the first 4 plaintext chars — display/debug only. */
    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    /** Last 4 plaintext chars, for masked display. */
    @Column(name = "key_last4", nullable = false, length = 8)
    private String keyLast4;

    /** Comma separated scope codes (e.g. {@code telemetry:write,rpc:execute}). */
    @Column(name = "scopes", nullable = false, length = 256)
    private String scopes;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private InstanceCredentialStatus status = InstanceCredentialStatus.ACTIVE;

    /** Logical ref to biz_user.uid / account.user.uid; no cross-database FK. */
    @Column(name = "owner_uid")
    private Long ownerUid;

    /** Issuance note (purpose / machine hint). */
    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
