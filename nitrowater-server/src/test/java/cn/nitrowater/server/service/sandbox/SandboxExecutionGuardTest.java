package cn.nitrowater.server.service.sandbox;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.ratelimit.RedisRateLimiter;
import cn.nitrowater.server.infra.ratelimit.SandboxGate;
import cn.nitrowater.server.web.dto.RunRequest;
import cn.nitrowater.server.web.dto.RunResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks the shared admission semantics used by both the HTTP endpoint and the agent tool.
 */
class SandboxExecutionGuardTest {

    private static SandboxProperties properties(int globalConcurrency, int maxQueue) {
        return new SandboxProperties(
                "http://localhost:2000",
                2_000,
                30_000,
                65_536,
                globalConcurrency,
                20_000,
                10,
                200,
                maxQueue,
                200,
                10_000,
                65_536,
                268_435_456L,
                15_000L,
                3,
                5_000L,
                3_000L);
    }

    @Test
    void rejectsWhenTheMinuteQuotaIsExhausted() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.allowSliding(anyString(), anyInt())).thenReturn(false);
        SandboxExecutionGuard guard = new SandboxExecutionGuard(limiter,
                new SandboxGate(properties(1, 4)), properties(1, 4), mock(SandboxService.class));

        assertThatThrownBy(() -> guard.admit("u1"))
                .isInstanceOfSatisfying(SandboxExecutionGuard.GuardException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.code()).isEqualTo("rate_limited");
                });
    }

    @Test
    void rejectsWhenTheDailyQuotaIsExhausted() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.allowSliding(anyString(), anyInt())).thenReturn(true);
        when(limiter.allowFixed(anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        SandboxExecutionGuard guard = new SandboxExecutionGuard(limiter,
                new SandboxGate(properties(1, 4)), properties(1, 4), mock(SandboxService.class));

        assertThatThrownBy(() -> guard.admit("u1"))
                .isInstanceOfSatisfying(SandboxExecutionGuard.GuardException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.code()).isEqualTo("daily_quota");
                });
    }

    @Test
    void mapsAFullGateQueueToQueueFull() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.allowSliding(anyString(), anyInt())).thenReturn(true);
        when(limiter.allowFixed(anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        SandboxExecutionGuard guard = new SandboxExecutionGuard(limiter,
                new SandboxGate(properties(1, 0)), properties(1, 0), mock(SandboxService.class));

        assertThatThrownBy(() -> guard.admit("u1"))
                .isInstanceOfSatisfying(SandboxExecutionGuard.GuardException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.code()).isEqualTo("queue_full");
                });
    }

    @Test
    void executeReleasesTheLeaseEvenAfterACompletedRun() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.allowSliding(anyString(), anyInt())).thenReturn(true);
        when(limiter.allowFixed(anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        SandboxProperties properties = properties(1, 1);
        SandboxGate gate = new SandboxGate(properties);
        SandboxService service = mock(SandboxService.class);
        RunRequest request = new RunRequest("java", "class Main {}", "");
        RunResponse response = new RunResponse("ok", "", "OK", 0, 1L, 1L, null);
        when(service.run(request)).thenReturn(response);
        SandboxExecutionGuard guard = new SandboxExecutionGuard(limiter, gate, properties, service);

        assertThat(guard.execute("u1", request)).isSameAs(response);
        // A leaked lease would make this second acquisition time out.
        try (SandboxGate.Lease ignored = gate.acquire("u1")) {
            assertThat(ignored).isNotNull();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("lease was not released", ex);
        }
    }

    @Test
    void rejectsAnAnonymousCaller() {
        SandboxExecutionGuard guard = new SandboxExecutionGuard(mock(RedisRateLimiter.class),
                new SandboxGate(properties(1, 4)), properties(1, 4), mock(SandboxService.class));

        assertThatThrownBy(() -> guard.admit(" "))
                .isInstanceOfSatisfying(SandboxExecutionGuard.GuardException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.code()).isEqualTo("unauthorized");
                });
    }
}
