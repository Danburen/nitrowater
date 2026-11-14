package cn.nitrowater.resource.service;

import cn.nitrowater.resource.infrastructure.exception.ResourceException;
import cn.nitrowater.resource.service.dto.ResourceMeta;
import cn.nitrowater.resource.service.dto.ResourceReadResult;
import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.infrastructure.domain.Resource;
import cn.nitrowater.resource.infrastructure.ResourceRepository;
import cn.nitrowater.resource.infrastructure.domain.ResourceStatus;
import cn.nitrowater.lib.common.io.ResourceType;
import cn.nitrowater.resource.infrastructure.domain.ResourceVisibility;
import cn.nitrowater.resource.infrastructure.domain.SourceType;
import cn.nitrowater.resource.infrastructure.domain.StorageBackend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Locks the read-side split between the two exit surfaces:
 *
 * <pre>
 *   GET /api/public/resource/{uuid}   anonymous  -&gt; PUBLIC rows only
 *   GET /api/resource/{uuid}          bearer     -&gt; PUBLIC rows + the caller's own PRIVATE rows
 * </pre>
 *
 * <p>The controller passes {@code null} for the anonymous endpoints and the JWT uid for the
 * authenticated ones, so every case below is decided by the {@code callerUid} handed to the service.
 * Presigning is off (the shipped default), which is also what forces the stream outcome.</p>
 */
@ExtendWith(MockitoExtension.class)
class ResourceAccessTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final long OWNER = 1001L;
    private static final long OTHER = 2002L;

    @Mock
    private ResourceRepository repository;
    @Mock
    private FileStorageRegistryFactory storageRegistry;
    @Mock
    private FileStorageService storage;
    @Mock
    private PresignedUrlCacheService readUrlCache;

    private ResourceService service;

    @BeforeEach
    void setUp() {
        ResourceProperties properties = new ResourceProperties("local", false, 600,
                10L * 1024 * 1024, 64L * 1024 * 1024, null, null, null, null);
        service = new ResourceService(repository, storageRegistry, properties, null, null, readUrlCache);
    }

    @Test
    void anonymousCallerReadsPublicResourceAsAStream() {
        stubReadable(ResourceVisibility.PUBLIC, null);

        ResourceReadResult result = service.read(UUID, null);

        // No presigned redirect: the default deployment serves every byte through this service.
        assertThat(result.redirectUrl()).isNull();
        assertThat(result.stream()).isNotNull();
        assertThat(result.attachment()).isFalse();
    }

    @Test
    void anonymousCallerCannotReadPrivateResource() {
        stubActive(ResourceVisibility.PRIVATE, OWNER);

        // The anonymous surface answers 404, not 403: it must not confirm that the uuid exists.
        assertNotFound(() -> service.read(UUID, null));
    }

    @Test
    void anonymousCallerCannotDownloadPrivateResource() {
        stubActive(ResourceVisibility.PRIVATE, OWNER);

        assertNotFound(() -> service.download(UUID, null));
    }

    @Test
    void anonymousCallerCannotReadPrivateMetadata() {
        stubActive(ResourceVisibility.PRIVATE, OWNER);

        assertNotFound(() -> service.meta(UUID, null));
    }

    @Test
    void ownerReadsOwnPrivateResource() {
        stubReadable(ResourceVisibility.PRIVATE, OWNER);

        ResourceReadResult result = service.read(UUID, OWNER);

        assertThat(result.stream()).isNotNull();
    }

    @Test
    void anotherAuthenticatedUserCannotReadPrivateResource() {
        stubActive(ResourceVisibility.PRIVATE, OWNER);

        assertForbidden(() -> service.read(UUID, OTHER));
    }

    @Test
    void anonymousDownloadOfPublicResourceIsAnAttachment() {
        stubReadable(ResourceVisibility.PUBLIC, null);

        ResourceReadResult result = service.download(UUID, null);

        assertThat(result.stream()).isNotNull();
        assertThat(result.attachment()).isTrue();
        assertThat(result.fileName()).isEqualTo("note.txt");
    }

    @Test
    void metadataOfPublicResourcePointsAtThePublicExit() {
        stubActive(ResourceVisibility.PUBLIC, null);

        ResourceMeta meta = service.meta(UUID, null);

        assertThat(meta.url()).isEqualTo("/api/public/resource/" + UUID);
    }

    @Test
    void metadataOfPrivateResourcePointsAtTheAuthenticatedExit() {
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(resource(ResourceVisibility.PRIVATE, OWNER)));

        ResourceMeta meta = service.meta(UUID, OWNER);

        assertThat(meta.url()).isEqualTo("/api/resource/" + UUID);
    }

    @Test
    void adminMetadataOfAnotherUsersPrivateResourcePointsAtTheAdminDownload() {
        stubActive(ResourceVisibility.PRIVATE, OWNER);

        // A super-admin can see the row but cannot use the owner-scoped read path, so the returned
        // url must be the admin download endpoint (otherwise the console's "open" button answers 403).
        ResourceMeta meta = service.adminGet(UUID, OTHER, true);

        assertThat(meta.url()).isEqualTo("/api/admin/resources/" + UUID + "/download");
    }

    /** Stubs a readable resource whose bytes come back from the (local) storage backend. */
    private void stubReadable(ResourceVisibility visibility, Long ownerUid) {
        Resource resource = stubActive(visibility, ownerUid);
        when(storageRegistry.get(StorageBackend.LOCAL)).thenReturn(storage);
        when(storage.get(resource.getBucketKey()))
                .thenAnswer(invocation -> new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
    }

    private Resource stubActive(ResourceVisibility visibility, Long ownerUid) {
        Resource resource = resource(visibility, ownerUid);
        when(repository.findByUuid(UUID)).thenReturn(Optional.of(resource));
        return resource;
    }

    private static Resource resource(ResourceVisibility visibility, Long ownerUid) {
        Resource resource = new Resource();
        resource.setUuid(UUID);
        resource.setApp("nitrowater");
        resource.setBackend(StorageBackend.LOCAL);
        resource.setBucketKey("nitrowater/samples/" + (ownerUid == null ? "system" : ownerUid)
                + "/2026/01/01/deadbeef.txt");
        resource.setVisibility(visibility);
        resource.setSourceType(SourceType.USER_UPLOADED);
        resource.setOwnerUid(ownerUid);
        resource.setOriginalName("note.txt");
        resource.setResourceType(ResourceType.TEXT);
        resource.setMimeType("text/plain");
        resource.setSizeBytes(5L);
        resource.setStatus(ResourceStatus.ACTIVE);
        resource.setCreatedAt(Instant.parse("2026-11-14T00:00:00Z"));
        return resource;
    }

    private static void assertForbidden(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResourceException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.code()).isEqualTo("forbidden");
                });
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResourceException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.code()).isEqualTo("not_found");
                });
    }
}
