package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.InstanceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** Persistence for {@link InstanceCredential}. */
public interface InstanceCredentialRepository
        extends JpaRepository<InstanceCredential, Long>, JpaSpecificationExecutor<InstanceCredential> {

    /**
     * @param keyHash SHA-256 hex of the presented api-key
     * @return the matching credential, if any
     */
    Optional<InstanceCredential> findByKeyHash(String keyHash);

    /**
     * @param keyId display id (kid)
     * @return the matching credential, if any
     */
    Optional<InstanceCredential> findByKeyId(String keyId);

    /**
     * @param keyId candidate display id
     * @return whether a credential with that kid already exists
     */
    boolean existsByKeyId(String keyId);
}
