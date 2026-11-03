package cn.nitrowater.server.ai.memory;

import cn.nitrowater.server.service.ai.AiMemoryMaintenance;
import cn.nitrowater.server.service.ai.AiMemoryService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Verifies the scheduled cleanup applies both configured retention windows. */
class AiMemoryMaintenanceTest {

    @Test
    void cleanupExpiresAndPurgesWithConfiguredWindows() {
        AiMemoryService memories = mock(AiMemoryService.class);
        AiMemoryMaintenance maintenance = new AiMemoryMaintenance(memories, 180, 30);

        maintenance.cleanup();

        verify(memories).expireStale(any(Instant.class), eq(Duration.ofDays(180)));
        verify(memories).purge(any(Instant.class), eq(Duration.ofDays(30)));
    }
}
