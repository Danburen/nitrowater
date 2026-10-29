package cn.nitrowater.resource.application;

import cn.nitrowater.resource.application.dto.ResourceMeta;
import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceRepository;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.ResourceVisibility;
import cn.nitrowater.resource.domain.SourceType;
import cn.nitrowater.resource.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.FileStorage;
import cn.nitrowater.resource.infrastructure.FileStorageRegistry;
import cn.nitrowater.resource.infrastructure.PresignedUrlCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the admin plugin-artifact publication path: the strict policy is applied before storage is
 * touched, the artifact lands under {@code app=nitrowater / group=<plugin>}, and a re-publish reuses
 * the existing row (stable uuid / download URL).
 */
@ExtendWith(MockitoExtension.class)
class ResourceServicePluginArtifactTest {

    private static final String KEY = "velochatx";
    private static final String NAME = "VeloChatX-1.2.3.jar";
    private static final byte[] JAR = new byte[] {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 1, 2, 3, 4, 5, 6};

    @Mock
    private ResourceRepository repository;
    @Mock
    private FileStorageRegistry storageRegistry;
    @Mock
    private FileStorage storage;
    @Mock
    private PresignedUrlCache readUrlCache;

    private ResourceService service;

    @BeforeEach
    void setUp() {
        ResourceProperties properties = new ResourceProperties(
                "local", 600, 10L * 1024 * 1024, 64L * 1024 * 1024, null, null, null, null, null);
        PluginArtifactPolicy policy = new PluginArtifactPolicy(properties);
        service = new ResourceService(repository, storageRegistry, properties,
                new FileTypeValidator(), policy, readUrlCache);
    }

    private void stubNewPublication() {
        when(repository.findFirstByAppAndGroupKeyAndOriginalNameAndSourceTypeOrderByCreatedAtDesc(
                eq(PluginArtifactPolicy.PLUGIN_APP), eq(KEY), eq(NAME), eq(SourceType.SYSTEM)))
                .thenReturn(Optional.empty());
        when(repository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storageRegistry.get(StorageBackend.LOCAL)).thenReturn(storage);
        when(storage.size(anyString())).thenReturn((long) JAR.length);
        when(storage.get(anyString())).thenAnswer(inv -> new ByteArrayInputStream(JAR));
    }

    @Test
    void publishesJarUnderThePluginNamespace() {
        stubNewPublication();

        ResourceMeta meta = service.publishPluginArtifact(
                KEY, NAME, "1.2.3", new ByteArrayInputStream(JAR), JAR.length);

        assertThat(meta.app()).isEqualTo(PluginArtifactPolicy.PLUGIN_APP);
        assertThat(meta.groupKey()).isEqualTo(KEY);
        assertThat(meta.name()).isEqualTo(NAME);
        assertThat(meta.version()).isEqualTo("1.2.3");
        assertThat(meta.status()).isEqualTo(ResourceStatus.ACTIVE);
        assertThat(meta.visibility()).isEqualTo(ResourceVisibility.PUBLIC);
        assertThat(meta.url()).startsWith("/api/public/resource/").contains(meta.uuid());
        verify(storage).put(anyString(), any(), anyLong(), eq("application/java-archive"));
        verify(readUrlCache).evict(meta.uuid());
    }

    @Test
    void republishReusesTheExistingUuid() {
        Resource existing = new Resource();
        existing.setUuid("11111111-2222-3333-4444-555555555555");
        existing.setBucketKey("nitrowater/velochatx/system/2026/01/01/deadbeef.jar");
        when(repository.findFirstByAppAndGroupKeyAndOriginalNameAndSourceTypeOrderByCreatedAtDesc(
                anyString(), anyString(), anyString(), eq(SourceType.SYSTEM)))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storageRegistry.get(StorageBackend.LOCAL)).thenReturn(storage);
        when(storage.size(anyString())).thenReturn((long) JAR.length);
        when(storage.get(anyString())).thenAnswer(inv -> new ByteArrayInputStream(JAR));

        ResourceMeta meta = service.publishPluginArtifact(
                KEY, NAME, "1.2.4", new ByteArrayInputStream(JAR), JAR.length);

        assertThat(meta.uuid()).isEqualTo(existing.getUuid());
        assertThat(meta.version()).isEqualTo("1.2.4");
    }

    @Test
    void rejectsNonJarBeforeTouchingStorage() {
        assertThatThrownBy(() -> service.publishPluginArtifact(
                KEY, "plugin.zip", null, new ByteArrayInputStream(JAR), JAR.length))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("unsupported_type"));

        verify(storageRegistry, never()).get(any());
    }

    @Test
    void rejectsInvalidKeyBeforeTouchingStorage() {
        assertThatThrownBy(() -> service.publishPluginArtifact(
                "BadKey", NAME, null, new ByteArrayInputStream(JAR), JAR.length))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("invalid_plugin_key"));

        verify(storageRegistry, never()).get(any());
    }

    @Test
    void rejectsOversizedJarBeforeTouchingStorage() {
        long tooLarge = ResourceProperties.DEFAULT_PLUGIN_MAX_SIZE_BYTES + 1;

        assertThatThrownBy(() -> service.publishPluginArtifact(
                KEY, NAME, null, new ByteArrayInputStream(JAR), tooLarge))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("too_large"));

        verify(storageRegistry, never()).get(any());
    }
}
