package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.infra.entity.InstanceRegistry;

import java.time.Instant;

/**
 * A registered VeloChatX instance for the admin console.
 *
 * @param id          primary key
 * @param instanceId  V-generated UUID
 * @param displayName human-friendly name (editable)
 * @param bootId      last reported process UUID (clone detection)
 * @param ownerUid    logical owner uid
 * @param firstSeenAt first bind time
 * @param lastSeenAt  last heartbeat time
 */
public record InstanceRegistryResp(
        Long id,
        String instanceId,
        String displayName,
        String bootId,
        Long ownerUid,
        Instant firstSeenAt,
        Instant lastSeenAt) {

    /**
     * @param row registry row
     * @return the wire view
     */
    public static InstanceRegistryResp from(InstanceRegistry row) {
        return new InstanceRegistryResp(
                row.getId(),
                row.getInstanceId(),
                row.getDisplayName(),
                row.getBootId(),
                row.getOwnerUid(),
                row.getFirstSeenAt(),
                row.getLastSeenAt());
    }
}
