package cn.nitrowater.resource.service;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.infrastructure.domain.Resource;
import cn.nitrowater.resource.infrastructure.ResourceRepository;
import cn.nitrowater.resource.infrastructure.domain.ResourceStatus;
import cn.nitrowater.resource.infrastructure.domain.SourceType;
import cn.nitrowater.resource.infrastructure.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.exception.ResourceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the proxy-upload path ({@code PUT /api/resource/upload/{uuid}} on the LOCAL backend): the
 * declared length is rejected <em>before</em> anything is written, so a caller cannot fill the disk
 * with an object that post-write validation would only delete again.
 */
@ExtendWith(MockitoExtension.class)
class ResourceServicePutBytesTest {

    private static final long OWNER = 1001L;
    private static final long OTHER = 2002L;
    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final long MAX_BYTES = 10L;

    @Mock
    private ResourceRepository repository;
    @Mock
    private FileStorageRegistryFactory storageRegistry;
    @Mock
    private FileStorageService storage;
    @Mock
    private PresignedUrlCacheService readUrlCache;

    private ResourceService newService() {
        // Only the repository / registry / size cap matter here; the rest are unused on this path.
        ResourceProperties properties = new ResourceProperties("local", false, 600, MAX_BYTES,
                64L * 1024 * 1024, null, null, null, null);
        return new ResourceService(repository, storageRegistry, properties, null, null, readUrlCache);
    }

    private static Resource pendingResource(Long ownerUid) {
        Resource resource = new Resource();
        resource.setUuid(UUID);
        resource.setApp("app");
        resource.setBackend(StorageBackend.LOCAL);
        resource.setBucketKey("app/_/" + ownerUid + "/2026/01/01/deadbeef");
        resource.setSourceType(SourceType.USER_UPLOADED);
        resource.setOwnerUid(ownerUid);
        resource.setStatus(ResourceStatus.PENDING);
        return resource;
    }

    @Test
    void rejectsAnOversizedLengthBeforeTouchingStorage() {
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(pendingResource(OWNER)));

        assertThatThrownBy(() -> newService().putBytes(UUID, OWNER,
                new ByteArrayInputStream(new byte[(int) MAX_BYTES + 1]), MAX_BYTES + 1, null))
                .isInstanceOfSatisfying(ResourceException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
                    assertThat(ex.code()).isEqualTo("too_large");
                });

        verifyNoInteractions(storageRegistry);
    }

    @Test
    void rejectsAnotherUsersResourceBeforeTouchingStorage() {
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(pendingResource(OTHER)));

        assertThatThrownBy(() -> newService().putBytes(UUID, OWNER,
                new ByteArrayInputStream(new byte[1]), 1, null))
                .isInstanceOfSatisfying(ResourceException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.code()).isEqualTo("not_owner");
                });

        verifyNoInteractions(storageRegistry);
    }
}
