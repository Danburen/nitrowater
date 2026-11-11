package cn.nitrowater.server.infra.ratelimit;

import cn.nitrowater.server.config.SandboxProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the admission semantics of {@link SandboxGate}: one run per user, a global concurrency
 * ceiling, a bounded wait queue, and leases that release exactly once.
 *
 * <p>The gate is deliberately in-process, so these tests exercise the real semaphores rather than
 * a mocked distributed lock.</p>
 */
class SandboxGateTest {

    private static SandboxProperties properties(int globalConcurrency, int maxQueue, long queueWaitMs) {
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
                queueWaitMs,
                10_000,
                65_536,
                268_435_456L,
                15_000L,
                3,
                5_000L,
                3_000L);
    }

    @Test
    void allowsDifferentUsersUpToGlobalConcurrency() throws Exception {
        SandboxGate gate = new SandboxGate(properties(2, 4, 200));

        try (SandboxGate.Lease first = gate.acquire("u1");
             SandboxGate.Lease second = gate.acquire("u2")) {
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
        }
    }

    @Test
    void serialisesRunsForTheSameUser() throws Exception {
        SandboxGate gate = new SandboxGate(properties(2, 4, 200));

        try (SandboxGate.Lease ignored = gate.acquire("u1")) {
            assertThatThrownBy(() -> gate.acquire("u1"))
                    .isInstanceOfSatisfying(SandboxGate.GateBusyException.class,
                            ex -> assertThat(ex.code()).isEqualTo("user_busy"));
        }

        // The slot must be reusable once the first lease is closed.
        try (SandboxGate.Lease ignored = gate.acquire("u1")) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void timesOutWhenTheGlobalSlotStaysBusy() throws Exception {
        SandboxGate gate = new SandboxGate(properties(1, 4, 200));

        try (SandboxGate.Lease ignored = gate.acquire("u1")) {
            assertThatThrownBy(() -> gate.acquire("u2"))
                    .isInstanceOfSatisfying(SandboxGate.GateBusyException.class,
                            ex -> assertThat(ex.code()).isEqualTo("queue_timeout"));
        }

        // Once the holder releases, the waiter's user slot must not leak.
        try (SandboxGate.Lease ignored = gate.acquire("u2")) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void rejectsNewArrivalsWhenTheQueueIsFull() throws Exception {
        SandboxGate gate = new SandboxGate(properties(1, 1, 1_000));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (SandboxGate.Lease holder = gate.acquire("u1")) {
            Future<?> waiter = pool.submit(() -> {
                try (SandboxGate.Lease ignored = gate.acquire("u2")) {
                    // Released immediately once the holder lets go.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (gate.waitingCount() < 1 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(gate.waitingCount()).isEqualTo(1);

            assertThatThrownBy(() -> gate.acquire("u3"))
                    .isInstanceOfSatisfying(SandboxGate.GateBusyException.class,
                            ex -> assertThat(ex.code()).isEqualTo("queue_full"));

            holder.close();
            waiter.get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void closingALeaseTwiceDoesNotLeakPermits() throws Exception {
        SandboxGate gate = new SandboxGate(properties(1, 4, 150));

        SandboxGate.Lease lease = gate.acquire("u1");
        lease.close();
        lease.close();

        // A leaked global permit would let u2 run concurrently with this holder.
        try (SandboxGate.Lease ignored = gate.acquire("u1")) {
            assertThatThrownBy(() -> gate.acquire("u2"))
                    .isInstanceOfSatisfying(SandboxGate.GateBusyException.class,
                            ex -> assertThat(ex.code()).isEqualTo("queue_timeout"));
        }
    }
}
