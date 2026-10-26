package cn.nitrowater.lib.utils.codec;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Snowflake} — 64-bit id generator
 * (timestamp &lt;&lt; 22 | datacenter &lt;&lt; 17 | worker &lt;&lt; 12 | sequence).
 *
 * <p>Clock-rollback branches are unreachable through the public API (they depend on
 * {@code System.currentTimeMillis()} going backwards), so the tests drive them
 * white-box via reflection on {@code lastTimestamp} — including the tolerate (sleep)
 * path, the reject path and the interrupted-during-recovery path.</p>
 */
class SnowflakeTest {

    /** Mirror of the private EPOCH constant (2024-01-01T00:00:00Z). */
    private static final long EPOCH = 1704067200000L;

    private static void setLastTimestamp(Snowflake snow, long value) throws Exception {
        Field field = Snowflake.class.getDeclaredField("lastTimestamp");
        field.setAccessible(true);
        field.setLong(snow, value);
    }

    // ------------------------------------------------------------------
    // Constructor validation: worker/datacenter must stay in [0, 31].
    // ------------------------------------------------------------------

    @Test
    void acceptsBoundaryWorkerAndDatacenterIds() {
        assertDoesNotThrow(() -> Snowflake.of(0, 0));
        assertDoesNotThrow(() -> Snowflake.of(31, 31));
    }

    @Test
    void rejectsOutOfRangeWorkerIds() {
        assertThrows(IllegalArgumentException.class, () -> Snowflake.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> Snowflake.of(32, 0));
    }

    @Test
    void rejectsOutOfRangeDatacenterIds() {
        assertThrows(IllegalArgumentException.class, () -> Snowflake.of(0, -1));
        assertThrows(IllegalArgumentException.class, () -> Snowflake.of(0, 32));
    }

    // ------------------------------------------------------------------
    // Bit layout: worker in bits 12..16, datacenter in bits 17..21, and the
    // timestamp field decodes to roughly "now" once EPOCH is added back.
    // ------------------------------------------------------------------

    @Test
    void embedsWorkerAndDatacenterBits() {
        long id = Snowflake.of(3, 5).nextId();
        assertEquals(3, (id >>> 12) & 0x1F);
        assertEquals(5, (id >>> 17) & 0x1F);
    }

    @Test
    void timestampComponentTracksWallClock() {
        long before = System.currentTimeMillis();
        long id = Snowflake.of(0, 0).nextId();
        long decoded = (id >>> 22) + EPOCH;
        assertTrue(decoded >= before - 1 && decoded <= System.currentTimeMillis() + 1,
                "decoded timestamp " + decoded + " not near wall clock");
    }

    // ------------------------------------------------------------------
    // Ordering and uniqueness under a monotonically advancing clock.
    // ------------------------------------------------------------------

    @Test
    void burstOfIdsIsStrictlyIncreasingAndUnique() {
        Snowflake snow = Snowflake.of(1, 1);
        long previous = snow.nextId();
        for (int i = 0; i < 9_999; i++) {
            long next = snow.nextId();
            assertTrue(next > previous, "id " + next + " did not increase");
            previous = next;
        }
    }

    // ------------------------------------------------------------------
    // Clock rollback (white-box): offset <= 5ms is tolerated by sleeping,
    // a larger offset is rejected outright, and an interrupt during the
    // recovery sleep surfaces as RuntimeException with the flag restored.
    // ------------------------------------------------------------------

    @Test
    void toleratesSmallClockRollbackBySleeping() throws Exception {
        Snowflake snow = Snowflake.of(0, 0);
        setLastTimestamp(snow, System.currentTimeMillis() + 3);
        assertDoesNotThrow(snow::nextId);
    }

    @Test
    void rejectsLargeClockRollback() throws Exception {
        Snowflake snow = Snowflake.of(0, 0);
        setLastTimestamp(snow, System.currentTimeMillis() + 10_000);
        RuntimeException raised = assertThrows(RuntimeException.class, snow::nextId);
        assertTrue(raised.getMessage().contains("Clock moved backwards"));
    }

    @Test
    void sequenceWrapWaitsForNextMillisecond() throws Exception {
        Snowflake snow = Snowflake.of(0, 0);
        Field sequenceField = Snowflake.class.getDeclaredField("sequence");
        sequenceField.setAccessible(true);
        Field timestampField = Snowflake.class.getDeclaredField("lastTimestamp");
        timestampField.setAccessible(true);
        // Plant sequence=4095 so the next call wraps to 0. If the call still lands in
        // the planted millisecond, nextId() must spin in waitUntilNextMillis; if the
        // wall clock already ticked over, the plain path runs instead — so retry until
        // a wrapped (strictly later) timestamp is observed.
        boolean wrapped = false;
        for (int attempt = 0; attempt < 100 && !wrapped; attempt++) {
            long planted = System.currentTimeMillis();
            timestampField.setLong(snow, planted);
            sequenceField.setLong(snow, 4095L);
            long id = snow.nextId();
            wrapped = ((id >>> 22) + EPOCH) > planted;
        }
        assertTrue(wrapped, "expected at least one same-millisecond sequence wrap");
    }

    @Test
    void interruptedDuringClockRecoverySurfacesRuntimeException() throws Exception {
        Snowflake snow = Snowflake.of(0, 0);
        RuntimeException raised = null;
        // Retry: on a slow machine the clock may already have passed the planted
        // timestamp before nextId() starts, taking the plain path instead.
        for (int attempt = 0; attempt < 20 && raised == null; attempt++) {
            setLastTimestamp(snow, System.currentTimeMillis() + 5);
            Thread.currentThread().interrupt();
            try {
                snow.nextId();
            } catch (RuntimeException e) {
                raised = e;
            } finally {
                Thread.interrupted(); // never leak the flag out of the test
            }
        }
        assertNotNull(raised, "expected the interrupted-recovery path to fire");
        assertTrue(raised.getMessage().contains("interrupted"));
    }
}
