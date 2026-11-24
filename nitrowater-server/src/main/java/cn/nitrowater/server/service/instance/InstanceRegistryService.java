package cn.nitrowater.server.service.instance;

import cn.nitrowater.server.config.InstanceLimits;
import cn.nitrowater.server.infra.entity.InstanceCredential;
import cn.nitrowater.server.infra.entity.InstanceRegistry;
import cn.nitrowater.server.infra.exception.InstanceException;
import cn.nitrowater.server.infra.repository.InstanceRegistryRepository;
import cn.nitrowater.server.infra.specification.InstanceRegistrySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The instance registry: first-bind registration, per-key quota, display-name assignment and the
 * admin rename / remove operations.
 *
 * <p>Identity is the {@code (keyId, instanceId)} pair — the key fixes the owner, the UUID fixes the
 * machine. A repeated heartbeat for a known instance is idempotent (only bookkeeping fields move),
 * so a restart never consumes a second slot. A unique index on the pair is the concurrency
 * backstop; soft-deleted rows release their slot and can be rebound.</p>
 */
@Service
public class InstanceRegistryService {

    private static final int NAME_SALT_LIMIT = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final InstanceRegistryRepository registry;
    private final InstanceNameGenerator nameGenerator;

    public InstanceRegistryService(InstanceRegistryRepository registry, InstanceNameGenerator nameGenerator) {
        this.registry = registry;
        this.nameGenerator = nameGenerator;
    }

    /**
     * Registers (or refreshes) an instance on first heartbeat.
     *
     * @param credential the authenticated key (fixes key id and owner)
     * @param instanceId V-generated UUID
     * @param bootId     last reported process UUID, for clone detection
     * @return the persisted registry row, including its display name
     * @throws InstanceException when the key already owns the maximum number of live instances
     */
    @Transactional
    public InstanceRegistry register(InstanceCredential credential, String instanceId, String bootId) {
        String keyId = credential.getKeyId();
        Long ownerUid = credential.getOwnerUid();

        var existing = registry.findByKeyIdAndInstanceIdAndRemovedAtIsNull(keyId, instanceId);
        if (existing.isPresent()) {
            InstanceRegistry row = existing.get();
            row.setBootId(bootId);
            row.setLastSeenAt(Instant.now());
            return registry.save(row);
        }

        long live = registry.countByKeyIdAndRemovedAtIsNull(keyId);
        if (live >= InstanceLimits.MAX_INSTANCES_PER_KEY) {
            throw InstanceException.limitExceeded("Api-key already owns "
                    + InstanceLimits.MAX_INSTANCES_PER_KEY + " instances");
        }

        InstanceRegistry row = new InstanceRegistry();
        row.setKeyId(keyId);
        row.setInstanceId(instanceId);
        row.setOwnerUid(ownerUid);
        row.setBootId(bootId);
        row.setDisplayName(availableName(instanceId, ownerUid));
        Instant now = Instant.now();
        row.setFirstSeenAt(now);
        row.setLastSeenAt(now);
        return registry.save(row);
    }

    /**
     * Renames an instance. Names must be unique among the owner's live instances so AI/UI name
     * addressing stays unambiguous.
     *
     * @param credential the key that owns the instance
     * @param instanceId instance UUID
     * @param displayName new display name
     * @return the updated row
     * @throws InstanceException when the instance is missing or the name is taken
     */
    @Transactional
    public InstanceRegistry rename(InstanceCredential credential, String instanceId, String displayName) {
        InstanceRegistry row = requireLive(credential.getKeyId(), instanceId);
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            throw InstanceException.badProtocol("Display name must not be blank");
        }
        if (registry.existsByOwnerUidAndDisplayNameAndRemovedAtIsNull(credential.getOwnerUid(), name)
                && !name.equals(row.getDisplayName())) {
            throw InstanceException.conflict("Display name already in use: " + name);
        }
        row.setDisplayName(name);
        return registry.save(row);
    }

    /**
     * Removes an instance by soft-delete, releasing its slot. The instance may re-register later
     * with the same UUID and receive a fresh display name.
     *
     * @param credential the key that owns the instance
     * @param instanceId instance UUID
     */
    @Transactional
    public void remove(InstanceCredential credential, String instanceId) {
        InstanceRegistry row = requireLive(credential.getKeyId(), instanceId);
        row.setRemovedAt(Instant.now());
        registry.save(row);
    }

    /**
     * @param credential the key whose instances to list
     * @param keyword    optional display name / UUID / boot id substring
     * @param page       zero-based page index
     * @param size       page size
     * @return one page of live instances, most recently seen first
     */
    @Transactional(readOnly = true)
    public Page<InstanceRegistry> list(InstanceCredential credential, String keyword, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "lastSeenAt").and(Sort.by("id"));
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)), sort);
        return registry.findAll(InstanceRegistrySpec.of(credential.getKeyId(), keyword), pageable);
    }

    /**
     * @param keyId      credential kid
     * @param instanceId instance UUID
     * @return the live registry row
     * @throws InstanceException when not found
     */
    @Transactional(readOnly = true)
    public InstanceRegistry requireLive(String keyId, String instanceId) {
        return registry.findByKeyIdAndInstanceIdAndRemovedAtIsNull(keyId, instanceId)
                .orElseThrow(() -> InstanceException.notFound("Instance not found: " + instanceId));
    }

    /** @return a name derived from the UUID, salted until it is free for this owner */
    private String availableName(String instanceId, Long ownerUid) {
        for (int salt = 0; salt < NAME_SALT_LIMIT; salt++) {
            String candidate = nameGenerator.generate(instanceId, salt);
            if (!registry.existsByOwnerUidAndDisplayNameAndRemovedAtIsNull(ownerUid, candidate)) {
                return candidate;
            }
        }
        throw InstanceException.conflict("Unable to allocate a unique display name");
    }
}
