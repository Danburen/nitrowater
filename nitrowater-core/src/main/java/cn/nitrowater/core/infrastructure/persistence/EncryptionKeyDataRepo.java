package cn.nitrowater.core.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import cn.nitrowater.core.entity.EncryptionDataKey;
import cn.nitrowater.core.entity.KeyStatus;

import java.util.List;
import java.util.Optional;

public interface EncryptionKeyDataRepo extends JpaRepository<EncryptionDataKey,Integer> {
    long countEncryptionDataKeysByKeyStatus(KeyStatus keyStatus);

    Optional<EncryptionDataKey> findByKeyId(String keyId);

    Optional<EncryptionDataKey> findFirstByKeyPurposeAndKeyStatusOrderByCreatedAtDesc(String keyPurpose, KeyStatus keyStatus);

    List<EncryptionDataKey> findByKeyPurposeAndKeyStatus(String keyPurpose, KeyStatus keyStatus);

    List<EncryptionDataKey> findByKeyPurposeIsNull();
}
