package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.InstanceRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link InstanceRegistry}. */
public interface InstanceRegistryRepository
        extends JpaRepository<InstanceRegistry, Long>, JpaSpecificationExecutor<InstanceRegistry> {

    /**
     * @param keyId      credential kid
     * @param instanceId V-generated UUID
     * @return the live row for this instance, if any
     */
    Optional<InstanceRegistry> findByKeyIdAndInstanceIdAndRemovedAtIsNull(String keyId, String instanceId);

    /**
     * @param keyId credential kid
     * @return number of instances still occupying a slot for this key
     */
    long countByKeyIdAndRemovedAtIsNull(String keyId);

    /**
     * @param ownerUid    owner uid
     * @param displayName candidate display name
     * @return whether a live instance of this owner already uses the name
     */
    boolean existsByOwnerUidAndDisplayNameAndRemovedAtIsNull(Long ownerUid, String displayName);

    /**
     * @param keyId credential kid
     * @return all live instances for the key, most recently seen first
     */
    List<InstanceRegistry> findByKeyIdAndRemovedAtIsNullOrderByLastSeenAtDesc(String keyId);
}
