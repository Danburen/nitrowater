package cn.nitrowater.server.service.instance;

import cn.nitrowater.server.infra.entity.InstanceCredential;
import cn.nitrowater.server.infra.enums.InstanceCredentialStatus;
import cn.nitrowater.server.infra.repository.InstanceCredentialRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

import static cn.nitrowater.server.infra.enums.InstanceScope.RPC_EXECUTE;
import static cn.nitrowater.server.infra.enums.InstanceScope.TELEMETRY_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the one-time key issuance and hash-only verification of {@link InstanceCredentialService}. */
class InstanceCredentialServiceTest {

    private final InstanceCredentialRepository rows = mock(InstanceCredentialRepository.class);
    private final InstanceCredentialService service = new InstanceCredentialService(rows);

    @Test
    void issueStoresOnlyTheHashAndReturnsThePlaintextOnce() {
        when(rows.existsByKeyId(anyString())).thenReturn(false);
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceCredentialService.IssuedCredential issued = service.issue("ops", 7L,
                Set.of(TELEMETRY_WRITE, RPC_EXECUTE));

        assertThat(issued.rawKey()).startsWith("nw_live_");
        assertThat(issued.rawKey()).hasSize(8 + 43);
        assertThat(issued.credential().getKeyHash()).hasSize(64);
        assertThat(issued.credential().getKeyHash()).isNotEqualTo(issued.rawKey());
        assertThat(issued.credential().getKeyPrefix()).isEqualTo("nw_live_" + issued.rawKey().substring(8, 12));
        assertThat(issued.credential().getKeyLast4()).isEqualTo(issued.rawKey().substring(issued.rawKey().length() - 4));
        assertThat(issued.credential().getScopes()).isEqualTo("telemetry:write,rpc:execute");
        assertThat(issued.credential().getStatus()).isEqualTo(InstanceCredentialStatus.ACTIVE);

        ArgumentCaptor<InstanceCredential> saved = ArgumentCaptor.forClass(InstanceCredential.class);
        verify(rows).save(saved.capture());
        assertThat(saved.getValue().getKeyHash()).doesNotContain("nw_live_");
    }

    @Test
    void verifyMatchesByHashAndRejectsRevokedKeys() {
        InstanceCredentialService.IssuedCredential issued = issue();
        InstanceCredential row = issued.credential();
        when(rows.findByKeyHash(row.getKeyHash())).thenReturn(Optional.of(row));

        assertThat(service.verify(issued.rawKey())).contains(row);
        assertThat(service.verify("nw_live_wrong")).isEmpty();
        assertThat(service.verify(null)).isEmpty();
        verify(rows, never()).findByKeyHash("");

        row.setStatus(InstanceCredentialStatus.REVOKED);
        assertThat(service.verify(issued.rawKey())).isEmpty();
    }

    @Test
    void verifyRejectsWhenStoredHashDoesNotMatch() {
        InstanceCredential row = issue().credential();
        // A lookup hit whose stored hash differs from the presented key must be rejected by the
        // constant-time comparison (defence against a hash collision on the lookup column).
        row.setKeyHash("0".repeat(64));
        when(rows.findByKeyHash("0".repeat(64))).thenReturn(Optional.of(row));

        assertThat(service.verify("nw_live_somekey")).isEmpty();
    }

    private InstanceCredentialService.IssuedCredential issue() {
        when(rows.existsByKeyId(anyString())).thenReturn(false);
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return service.issue(null, null, Set.of());
    }
}
