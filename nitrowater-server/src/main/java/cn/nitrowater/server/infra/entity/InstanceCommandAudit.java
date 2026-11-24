package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.InstanceCommandResult;
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
 * Metadata of a dispatched remote command.
 *
 * <p>Never stores parameters or status payloads — those are monitoring data and stay in memory.
 * Only the identity of the command and its outcome are kept for troubleshooting.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "instance_command_audit")
public class InstanceCommandAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "command_id", nullable = false, length = 64)
    private String commandId;

    /** Logical ref to instance_credential.key_id. */
    @Column(name = "key_id", length = 64)
    private String keyId;

    @Column(name = "instance_id", nullable = false, length = 128)
    private String instanceId;

    /** RPC method name from the V-side whitelist (status / broadcast / onlinePlayers / reload). */
    @Column(name = "method", nullable = false, length = 64)
    private String method;

    /** Logical ref to account.user.uid of the admin who issued the command. */
    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "result", columnDefinition = "tinyint UNSIGNED")
    private InstanceCommandResult result;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();
}
