package cn.nitrowater.resource.application;

import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceRepository;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.FileStorage;
import cn.nitrowater.resource.infrastructure.FileStorageRegistry;
import cn.nitrowater.resource.infrastructure.PresignedUrlCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Locks the abandoned-upload reaper: stale PENDING rows lose their bytes and become ORPHAN, while
 * recent rows, disabled cleanup, and storage failures leave the row pending for a later retry.
 */
@ExtendWith(MockitoExtension.class)
class ResourceCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final String OBJECT_KEY = "nitrowater/velochatx/system/2026/10/27/deadbeef.jar";

    @Mock
    private ResourceRepository repository;
    @Mock
    private FileStorageRegistry storageRegistry;
    @Mock
    private FileStorage storage;
    @Mock
    private PresignedUrlCache readUrlCache;

    private ResourceCleanupService service(int orphanPendingMinutes) {
        return new ResourceCleanupService(repository, storageRegistry, readUrlCache, orphanPendingMinutes);
    }

    private static Resource pending() {
        Resource resource = new Resource();
        resource.setUuid(UUID);
        resource.setBucketKey(OBJECT_KEY);
        resource.setBackend(StorageBackend.LOCAL);
        resource.setStatus(ResourceStatus.PENDING);
        resource.setUpdatedAt(NOW.minusSeconds(3600));
        return resource;
    }

    @Test
    void reapsStalePendingUpload() {
        Resource stale = pending();
        when(repository.findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(ResourceStatus.PENDING), any(Instant.class))).thenReturn(List.of(stale));
        when(storageRegistry.get(StorageBackend.LOCAL)).thenReturn(storage);
        when(storage.exists(OBJECT_KEY)).thenReturn(true);

        int reaped = service(60).reapPendingAt(NOW);

        assertThat(reaped).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(ResourceStatus.ORPHAN);
        assertThat(stale.getUpdatedAt()).isEqualTo(NOW);
        verify(storage).remove(OBJECT_KEY);
        verify(repository).save(stale);
        verify(readUrlCache).evict(UUID);
    }

    @Test
    void marksOrphanWhenTheObjectIsAlreadyGone() {
        Resource stale = pending();
        when(repository.findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(ResourceStatus.PENDING), any(Instant.class))).thenReturn(List.of(stale));
        when(storageRegistry.get(StorageBackend.LOCAL)).thenReturn(storage);
        when(storage.exists(OBJECT_KEY)).thenReturn(false);

        int reaped = service(60).reapPendingAt(NOW);

        assertThat(reaped).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(ResourceStatus.ORPHAN);
        verify(storage, never()).remove(OBJECT_KEY);
        verify(repository).save(stale);
    }

    @Test
    void keepsPendingWhenStorageRemovalFails() {
        Resource stale = pending();
        when(repository.findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(ResourceStatus.PENDING), any(Instant.class))).thenReturn(List.of(stale));
        when(storageRegistry.get(StorageBackend.LOCAL)).thenReturn(storage);
        when(storage.exists(OBJECT_KEY)).thenReturn(true);
        doThrow(new RuntimeException("storage down")).when(storage).remove(OBJECT_KEY);

        int reaped = service(60).reapPendingAt(NOW);

        assertThat(reaped).isZero();
        assertThat(stale.getStatus()).isEqualTo(ResourceStatus.PENDING);
        verify(repository, never()).save(stale);
        verify(readUrlCache, never()).evict(UUID);
    }

    @Test
    void doesNothingWhenNoStaleRowsExist() {
        when(repository.findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(ResourceStatus.PENDING), any(Instant.class))).thenReturn(List.of());

        assertThat(service(60).reapPendingAt(NOW)).isZero();

        verifyNoInteractions(storageRegistry, readUrlCache);
    }

    @Test
    void doesNothingWhenCleanupIsDisabled() {
        assertThat(service(0).reapPendingAt(NOW)).isZero();

        verifyNoInteractions(repository, storageRegistry, readUrlCache);
    }
}
