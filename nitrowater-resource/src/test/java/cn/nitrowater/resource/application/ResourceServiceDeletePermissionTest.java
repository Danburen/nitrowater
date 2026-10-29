package cn.nitrowater.resource.application;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceRepository;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.SourceType;
import cn.nitrowater.resource.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.FileStorage;
import cn.nitrowater.resource.infrastructure.FileStorageRegistry;
import cn.nitrowater.resource.infrastructure.PresignedUrlCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Locks the delete permission model of the file-management console:
 *
 * <pre>
 *   ROLE_SUPER_ADMIN           -&gt; allowed (system resources and other users' files included)
 *   otherwise, SYSTEM source   -&gt; 403 system_resource
 *   otherwise, owner == caller -&gt; allowed
 *   otherwise                  -&gt; 403 not_owner
 * </pre>
 *
 * <p>Also pins the end-user {@code delete} path (always non-super) and that a non-super caller can
 * never touch system resources, so the SUPER_ADMIN bypass is the only way through.</p>
 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceDeletePermissionTest {

    private static final long OWNER = 1001L;
    private static final long OTHER = 2002L;
    private static final String UUID = "11111111-2222-3333-4444-555555555555";

    @Mock
    private ResourceRepository repository;
    @Mock
    private FileStorageRegistry storageRegistry;
    @Mock
    private FileStorage storage;
    @Mock
    private PresignedUrlCache readUrlCache;

    private ResourceService newService() {
        // properties / validator / policy are unused by the delete paths; pass nulls to keep the fixture small.
        return new ResourceService(repository, storageRegistry, null, null, null, readUrlCache);
    }

    private Resource resource(SourceType sourceType, Long ownerUid, ResourceStatus status) {
        Resource resource = new Resource();
        resource.setUuid(UUID);
        resource.setApp("app");
        resource.setBackend(StorageBackend.LOCAL);
        resource.setBucketKey("app/_/" + ownerUid + "/2026/01/01/deadbeef");
        resource.setSourceType(sourceType);
        resource.setOwnerUid(ownerUid);
        resource.setStatus(status);
        return resource;
    }

    private void stubLookup(Resource resource) {
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(resource));
        when(storageRegistry.get(StorageBackend.LOCAL)).thenReturn(storage);
    }

    @Test
    void ownerDeletesOwnUpload() {
        Resource resource = resource(SourceType.USER_UPLOADED, OWNER, ResourceStatus.ACTIVE);
        stubLookup(resource);

        newService().delete(UUID, OWNER);

        verify(storage).remove(resource.getBucketKey());
        verify(repository).save(resource);
        verify(readUrlCache).evict(UUID);
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.DELETED);
    }

    @Test
    void adminDeletesOwnUpload() {
        Resource resource = resource(SourceType.USER_UPLOADED, OWNER, ResourceStatus.ACTIVE);
        stubLookup(resource);

        newService().adminDelete(UUID, OWNER, false);

        verify(storage).remove(resource.getBucketKey());
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.DELETED);
    }

    @Test
    void adminCannotDeleteAnotherUsersUpload() {
        Resource resource = resource(SourceType.USER_UPLOADED, OTHER, ResourceStatus.ACTIVE);
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> newService().adminDelete(UUID, OWNER, false))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("not_owner"));

        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.ACTIVE);
        verify(repository, never()).save(resource);
        verifyNoInteractions(storageRegistry);
    }

    @Test
    void superAdminDeletesAnotherUsersUpload() {
        Resource resource = resource(SourceType.USER_UPLOADED, OTHER, ResourceStatus.ACTIVE);
        stubLookup(resource);

        newService().adminDelete(UUID, OWNER, true);

        verify(storage).remove(resource.getBucketKey());
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.DELETED);
    }

    @Test
    void nonSuperAdminCannotDeleteSystemResource() {
        Resource resource = resource(SourceType.SYSTEM, null, ResourceStatus.ACTIVE);
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> newService().adminDelete(UUID, OWNER, false))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("system_resource"));

        verify(repository, never()).save(resource);
        verifyNoInteractions(storageRegistry);
    }

    @Test
    void superAdminDeletesSystemResource() {
        Resource resource = resource(SourceType.SYSTEM, null, ResourceStatus.ACTIVE);
        stubLookup(resource);

        newService().adminDelete(UUID, OWNER, true);

        verify(storage).remove(resource.getBucketKey());
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.DELETED);
    }

    @Test
    void owningAlreadyDeletedResourceIsIdempotent() {
        Resource resource = resource(SourceType.USER_UPLOADED, OWNER, ResourceStatus.DELETED);
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(resource));

        newService().delete(UUID, OWNER);

        verifyNoInteractions(storageRegistry);
        verify(repository, never()).save(resource);
    }
}
