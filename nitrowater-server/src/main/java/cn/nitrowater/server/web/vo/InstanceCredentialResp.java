package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.infra.entity.InstanceCredential;
import cn.nitrowater.server.infra.enums.InstanceCredentialStatus;
import cn.nitrowater.server.infra.enums.InstanceScope;

import java.time.Instant;
import java.util.List;

/**
 * Masked view of an instance api-key for the admin console — never exposes the hash or plaintext.
 *
 * @param id         primary key (used by revoke / instance operations)
 * @param keyId      kid, display id
 * @param keyPrefix  {@code nw_live_} plus the first 4 chars
 * @param keyLast4   last 4 chars
 * @param scopes     granted scope codes
 * @param status     lifecycle status
 * @param ownerUid   logical owner uid (nullable)
 * @param remark     admin note (nullable)
 * @param createdAt  issuance time
 * @param revokedAt  revocation time (nullable)
 * @param lastUsedAt last successful authentication (nullable)
 */
public record InstanceCredentialResp(
        Long id,
        String keyId,
        String keyPrefix,
        String keyLast4,
        List<String> scopes,
        InstanceCredentialStatus status,
        Long ownerUid,
        String remark,
        Instant createdAt,
        Instant revokedAt,
        Instant lastUsedAt) {

    /**
     * @param credential credential row
     * @return the masked wire view
     */
    public static InstanceCredentialResp from(InstanceCredential credential) {
        return new InstanceCredentialResp(
                credential.getId(),
                credential.getKeyId(),
                credential.getKeyPrefix(),
                credential.getKeyLast4(),
                InstanceScope.parse(credential.getScopes()).stream().map(InstanceScope::getCode).toList(),
                credential.getStatus(),
                credential.getOwnerUid(),
                credential.getRemark(),
                credential.getCreatedAt(),
                credential.getRevokedAt(),
                credential.getLastUsedAt());
    }
}
