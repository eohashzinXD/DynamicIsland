package dev.android.dynamicisland.island.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure JVM regression tests; wall and monotonic time are always supplied explicitly. */
public class RecordingClockTest {
    private static final long WALL = 1_000_000L;
    private static final long ELAPSED = 10_000L;
    private static final long ORIGINAL = WALL - 10_000L;

    @Test
    public void fixedNativeTimestampIsPreserved() {
        RecordingClock clock = new RecordingClock();
        clock.start(ORIGINAL, true, WALL, ELAPSED);
        assertTrue(clock.matchesSystemBase(ORIGINAL));
        for (long delta : new long[] {0L, 500L, 2_000L, 30_000L}) {
            clock.update(ORIGINAL, WALL + delta, ELAPSED + delta);
            assertEquals(ORIGINAL, clock.base(WALL + delta, ELAPSED + delta));
            assertTrue(clock.matchesSystemBase(ORIGINAL));
        }
    }

    @Test
    public void rollingNearNowNativeTimestampsDoNotResetElapsed() {
        RecordingClock clock = new RecordingClock();
        clock.start(ORIGINAL, false, WALL, ELAPSED);
        for (long delta = 0L; delta <= 4_000L; delta += 400L) {
            long rolling = WALL + delta - 100L;
            clock.update(rolling, WALL + delta, ELAPSED + delta);
            assertEquals(ORIGINAL, clock.base(WALL + delta, ELAPSED + delta));
            assertEquals(10_000L + delta,
                    WALL + delta - clock.base(WALL + delta, ELAPSED + delta));
            assertFalse(clock.matchesSystemBase(rolling));
        }
    }

    @Test
    public void rapidUpdatesFromRecordingStartDoNotAccumulateSubsecondResets() {
        RecordingClock clock = new RecordingClock();
        clock.start(WALL, true, WALL, ELAPSED);
        for (long delta = 100L; delta <= 5000L; delta += 100L) {
            clock.update(WALL + delta, WALL + delta, ELAPSED + delta);
            assertEquals(WALL, clock.base(WALL + delta, ELAPSED + delta));
        }
    }

    @Test
    public void pendingCorrectionDoesNotMatureJustBecauseItsTimestampGetsOlder() {
        RecordingClock clock = new RecordingClock();
        clock.start(ORIGINAL, true, WALL, ELAPSED);
        long candidate = WALL - 1400L;
        clock.update(candidate, WALL, ELAPSED);
        clock.update(candidate, WALL + 200L, ELAPSED + 200L);
        assertEquals(ORIGINAL, clock.base(WALL + 200L, ELAPSED + 200L));
        clock.update(candidate, WALL + 1500L, ELAPSED + 1500L);
        assertEquals(candidate, clock.base(WALL + 1500L, ELAPSED + 1500L));
    }

    @Test
    public void stableNearZeroCorrectionWaitsUntil1500Milliseconds() {
        RecordingClock clock = new RecordingClock();
        clock.start(ORIGINAL, false, WALL, ELAPSED);
        clock.update(WALL, WALL, ELAPSED);
        clock.update(WALL, WALL + 1_499L, ELAPSED + 1_499L);
        assertEquals(ORIGINAL, clock.base(WALL + 1_499L, ELAPSED + 1_499L));
        assertFalse(clock.matchesSystemBase(WALL));

        clock.update(WALL, WALL + 1_500L, ELAPSED + 1_500L);
        assertEquals(WALL, clock.base(WALL + 1_500L, ELAPSED + 1_500L));
        assertTrue(clock.matchesSystemBase(WALL));
    }

    @Test
    public void normalDurationCorrectionOlderThan1500MillisecondsIsImmediate() {
        RecordingClock clock = new RecordingClock();
        clock.start(ORIGINAL, false, WALL, ELAPSED);
        long corrected = WALL - 1_501L;
        clock.update(corrected, WALL, ELAPSED);
        assertEquals(corrected, clock.base(WALL, ELAPSED));
        assertTrue(clock.matchesSystemBase(corrected));
    }

    @Test
    public void wallClockJumpsDoNotChangeElapsedDuration() {
        RecordingClock clock = new RecordingClock();
        clock.start(ORIGINAL, true, WALL, ELAPSED);
        long forwardWall = WALL + 3_600_000L;
        clock.update(ORIGINAL, forwardWall, ELAPSED + 1_000L);
        assertEquals(11_000L, forwardWall - clock.base(forwardWall, ELAPSED + 1_000L));

        long backwardWall = WALL - 3_600_000L;
        clock.update(ORIGINAL, backwardWall, ELAPSED + 2_000L);
        assertEquals(12_000L, backwardWall - clock.base(backwardWall, ELAPSED + 2_000L));
        assertTrue(clock.matchesSystemBase(ORIGINAL));
    }

    @Test
    public void returningToOriginalNativeTimestampClearsPendingCorrection() {
        RecordingClock clock = new RecordingClock();
        clock.start(ORIGINAL, true, WALL, ELAPSED);
        clock.update(WALL, WALL, ELAPSED);
        assertFalse(clock.matchesSystemBase(ORIGINAL));

        clock.update(ORIGINAL, WALL + 500L, ELAPSED + 500L);
        assertTrue(clock.matchesSystemBase(ORIGINAL));
        assertEquals(ORIGINAL, clock.base(WALL + 500L, ELAPSED + 500L));

        // Re-observing the discarded candidate starts a fresh stability window.
        clock.update(WALL, WALL + 1_499L, ELAPSED + 1_499L);
        clock.update(WALL, WALL + 1_500L, ELAPSED + 1_500L);
        assertFalse(clock.matchesSystemBase(WALL));
        assertEquals(ORIGINAL, clock.base(WALL + 1_500L, ELAPSED + 1_500L));
    }
}
