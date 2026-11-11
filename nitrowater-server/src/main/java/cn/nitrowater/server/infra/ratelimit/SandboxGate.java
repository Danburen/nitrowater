package cn.nitrowater.server.infra.ratelimit;

import cn.nitrowater.server.config.SandboxProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admission gate for server-side code execution.
 *
 * <p>Two levels of serialisation are applied: at most one run per user, and at most
 * {@code globalConcurrency} runs overall. A request that cannot get a slot waits up to
 * {@code queueWaitMs}; once {@code maxQueue} requests are already waiting, new arrivals are
 * rejected immediately instead of piling up.</p>
 *
 * <p>The gate is in-process. That is deliberate: the server is deployed as a single instance and
 * a distributed semaphore would add Redis round-trips to every run. If the service is ever scaled
 * out, replace this with a Redis lease or lower {@code globalConcurrency} per instance.</p>
 */
@Component
public class SandboxGate {

    private final Semaphore global;
    private final ConcurrentHashMap<String, UserSlot> users = new ConcurrentHashMap<>();
    private final AtomicInteger waiting = new AtomicInteger();
    private final int maxQueue;
    private final long queueWaitMs;

    public SandboxGate(SandboxProperties properties) {
        this.global = new Semaphore(properties.globalConcurrency(), true);
        this.maxQueue = properties.maxQueue();
        this.queueWaitMs = properties.queueWaitMs();
    }

    /**
     * Acquires a run slot for {@code uid}, waiting up to the configured timeout.
     *
     * @param uid the authenticated user id
     * @return a lease that must be closed when the run finishes
     * @throws GateBusyException when the queue is full, the wait times out, or the user already
     *         has a run that does not finish within the wait budget
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public Lease acquire(String uid) throws InterruptedException {
        return acquire(uid, queueWaitMs);
    }

    /**
     * Acquires a run slot with an explicit wait budget.
     *
     * @param uid the authenticated user id
     * @param waitMs maximum time to wait for both the per-user and global permits
     * @return a lease that must be closed when the run finishes
     * @throws GateBusyException when the queue is full, the wait times out, or the user already
     *         has a run that does not finish within the wait budget
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public Lease acquire(String uid, long waitMs) throws InterruptedException {
        long budget = Math.max(0L, waitMs);
        if (waiting.incrementAndGet() > maxQueue) {
            waiting.decrementAndGet();
            throw new GateBusyException("queue_full", "Sandbox queue is full, please retry shortly");
        }

        UserSlot slot = users.compute(uid, (key, existing) -> {
            UserSlot current = existing == null ? new UserSlot() : existing;
            current.references.incrementAndGet();
            return current;
        });

        boolean userAcquired = false;
        boolean globalAcquired = false;
        try {
            if (!slot.semaphore.tryAcquire(budget, TimeUnit.MILLISECONDS)) {
                throw new GateBusyException("user_busy", "Your previous run is still in progress");
            }
            userAcquired = true;
            if (!global.tryAcquire(budget, TimeUnit.MILLISECONDS)) {
                throw new GateBusyException("queue_timeout", "Sandbox queue wait timed out, please retry");
            }
            globalAcquired = true;
            return new Lease(() -> {
                global.release();
                slot.semaphore.release();
                releaseReference(uid, slot);
            });
        } finally {
            if (!globalAcquired) {
                if (userAcquired) {
                    slot.semaphore.release();
                }
                releaseReference(uid, slot);
            }
            waiting.decrementAndGet();
        }
    }

    /** Number of requests currently inside {@link #acquire(String)}; used by tests. */
    int waitingCount() {
        return waiting.get();
    }

    /**
     * Captures the current admission pressure so callers (the agent) can decide whether to ask the
     * user before blocking on the queue.
     *
     * @return an immutable snapshot of the queue and slot state
     */
    public Snapshot snapshot() {
        return new Snapshot(waiting.get(), global.availablePermits(), maxQueue, queueWaitMs);
    }

    /** Drops the per-user slot once no request for that user is holding or waiting on it. */
    private void releaseReference(String uid, UserSlot slot) {
        users.computeIfPresent(uid, (key, current) -> {
            if (current != slot) {
                return current;
            }
            return current.references.decrementAndGet() == 0 ? null : current;
        });
    }

    /** Per-user serialisation slot plus a reference count so idle entries can be evicted. */
    private static final class UserSlot {
        private final Semaphore semaphore = new Semaphore(1, true);
        private final AtomicInteger references = new AtomicInteger();
    }

    /**
     * Point-in-time view of the gate.
     *
     * @param waiting requests currently waiting for a slot
     * @param availableSlots free global execution slots
     * @param maxQueue configured queue ceiling
     * @param queueWaitMs configured wait budget per request
     */
    public record Snapshot(int waiting, int availableSlots, int maxQueue, long queueWaitMs) {
    }

    /** A held run slot; closing it releases both the global and per-user permits. */
    public static final class Lease implements AutoCloseable {
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Runnable release) {
            this.release = release;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }

    /** Raised when the gate cannot admit a request within its budget. */
    public static final class GateBusyException extends RuntimeException {
        private final String code;

        public GateBusyException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
