package cn.nitrowater.server.service.instance;

import cn.nitrowater.server.infra.entity.InstanceCredential;
import cn.nitrowater.server.infra.entity.InstanceRegistry;
import cn.nitrowater.server.infra.exception.InstanceException;
import cn.nitrowater.server.infra.repository.InstanceRegistryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers first-bind registration, the per-key quota, and slot release on removal. */
class InstanceRegistryServiceTest {

    private final InstanceRegistryRepository rows = mock(InstanceRegistryRepository.class);
    private final InstanceNameGenerator names = new InstanceNameGenerator();
    private final InstanceRegistryService service = new InstanceRegistryService(rows, names);

    @Test
    void firstBindRegistersWithGeneratedNameAndOwner() {
        InstanceCredential credential = credential();
        when(rows.findByKeyIdAndInstanceIdAndRemovedAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(rows.countByKeyIdAndRemovedAtIsNull("kid-1")).thenReturn(0L);
        when(rows.existsByOwnerUidAndDisplayNameAndRemovedAtIsNull(any(), anyString()))
                .thenReturn(false);
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceRegistry row = service.register(credential, "uuid-1", "boot-1");

        assertThat(row.getKeyId()).isEqualTo("kid-1");
        assertThat(row.getInstanceId()).isEqualTo("uuid-1");
        assertThat(row.getOwnerUid()).isEqualTo(7L);
        assertThat(row.getDisplayName()).startsWith("instance-");
        assertThat(row.getBootId()).isEqualTo("boot-1");
        assertThat(row.getRemovedAt()).isNull();
    }

    @Test
    void repeatedHeartbeatIsIdempotentAndDoesNotConsumeASlot() {
        InstanceCredential credential = credential();
        InstanceRegistry existing = existingRow();
        when(rows.findByKeyIdAndInstanceIdAndRemovedAtIsNull("kid-1", "uuid-1"))
                .thenReturn(Optional.of(existing));
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceRegistry row = service.register(credential, "uuid-1", "boot-2");

        assertThat(row).isSameAs(existing);
        assertThat(row.getBootId()).isEqualTo("boot-2");
        verify(rows, never()).countByKeyIdAndRemovedAtIsNull(anyString());
    }

    @Test
    void quotaRejectsTheFourthInstance() {
        InstanceCredential credential = credential();
        when(rows.findByKeyIdAndInstanceIdAndRemovedAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(rows.countByKeyIdAndRemovedAtIsNull("kid-1")).thenReturn(3L);

        assertThatThrownBy(() -> service.register(credential, "uuid-4", "boot-4"))
                .isInstanceOf(InstanceException.class)
                .extracting(ex -> ((InstanceException) ex).code())
                .isEqualTo("instance_limit_exceeded");
        verify(rows, never()).save(any());
    }

    @Test
    void saltIsAppliedWhenGeneratedNameIsTaken() {
        InstanceCredential credential = credential();
        String baseName = names.generate("uuid-1", 0);
        when(rows.findByKeyIdAndInstanceIdAndRemovedAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(rows.countByKeyIdAndRemovedAtIsNull("kid-1")).thenReturn(0L);
        when(rows.existsByOwnerUidAndDisplayNameAndRemovedAtIsNull(eq(7L), anyString()))
                .thenAnswer(invocation -> baseName.equals(invocation.getArgument(1)));
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceRegistry row = service.register(credential, "uuid-1", "boot-1");

        assertThat(row.getDisplayName()).isEqualTo(names.generate("uuid-1", 1));
    }

    @Test
    void removeSoftDeletesAndReleasesTheSlot() {
        InstanceCredential credential = credential();
        InstanceRegistry existing = existingRow();
        when(rows.findByKeyIdAndInstanceIdAndRemovedAtIsNull("kid-1", "uuid-1"))
                .thenReturn(Optional.of(existing));
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.remove(credential, "uuid-1");

        assertThat(existing.getRemovedAt()).isNotNull();
        ArgumentCaptor<InstanceRegistry> saved = ArgumentCaptor.forClass(InstanceRegistry.class);
        verify(rows).save(saved.capture());
        assertThat(saved.getValue().getRemovedAt()).isNotNull();
    }

    private static InstanceCredential credential() {
        InstanceCredential credential = new InstanceCredential();
        credential.setId(1L);
        credential.setKeyId("kid-1");
        credential.setOwnerUid(7L);
        return credential;
    }

    private static InstanceRegistry existingRow() {
        InstanceRegistry row = new InstanceRegistry();
        row.setId(2L);
        row.setKeyId("kid-1");
        row.setInstanceId("uuid-1");
        row.setDisplayName("instance-abc123");
        row.setOwnerUid(7L);
        row.setBootId("boot-1");
        row.setFirstSeenAt(Instant.now());
        row.setLastSeenAt(Instant.now());
        return row;
    }

}
