package dev.android.dynamicisland.island.source;

import static org.junit.Assert.*;

import org.junit.Test;

/** Pure policy tests: timestamps are explicit and no Android clock or runner is needed. */
public class MediaForegroundGateTest {
    private static final String PLAYER = "com.music";
    private static final String OTHER = "com.android.launcher";
    private final MediaForegroundGate gate = new MediaForegroundGate();
    private final Object first = new Object();
    private final Object second = new Object();

    @Test
    public void confirmedMatchingAppIsHiddenOnFirstDecision() {
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, true, 1000L));
        assertEquals(-1L, gate.retryAt());
        assertTrue(gate.owns(first));
        assertFalse(gate.owns(second));
    }

    @Test
    public void confirmedOtherAppIsAdmittedImmediatelyWithoutGenericDelay() {
        assertFalse(gate.shouldHide(first, PLAYER, OTHER, true, 1000L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void cachedMatchingAppRemainsHiddenBeyondTheRetryWindow() {
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, false, 1000L));
        assertEquals(-1L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, false, 5000L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void uncertainNewSessionHasOneFixedDeadlineAndIsAdmittedAtItsBoundary() {
        assertEquals(180L, MediaForegroundGate.RECHECK_MS);
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1000L));
        assertEquals(1180L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1100L));
        assertEquals(1180L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1179L));
        assertEquals(1180L, gate.retryAt());
        assertFalse(gate.shouldHide(first, PLAYER, null, false, 1180L));
        assertEquals(-1L, gate.retryAt());
        assertFalse(gate.shouldHide(first, PLAYER, null, false, 5000L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void cachedOtherAppIsStillUncertainForANewSession() {
        assertTrue(gate.shouldHide(first, PLAYER, OTHER, false, 2000L));
        assertEquals(2180L, gate.retryAt());
        assertFalse(gate.shouldHide(first, PLAYER, OTHER, false, 2180L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void matchingForegroundBeforeDeadlineCancelsPendingRetry() {
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1000L));
        assertEquals(1180L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, true, 1100L));
        assertEquals(-1L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, true, 1180L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void cachedMatchingForegroundAlsoCancelsPendingRetry() {
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1000L));
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, false, 1100L));
        assertEquals(-1L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, false, 1500L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void confirmedOtherForegroundEndsPendingDeferralImmediately() {
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1000L));
        assertFalse(gate.shouldHide(first, PLAYER, OTHER, true, 1001L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void admittedBackgroundSessionDoesNotFlickerOnUnknownSnapshots() {
        assertFalse(gate.shouldHide(first, PLAYER, OTHER, true, 1000L));
        assertFalse(gate.shouldHide(first, PLAYER, null, false, 1001L));
        assertFalse(gate.shouldHide(first, PLAYER, OTHER, false, 1100L));
        assertFalse(gate.shouldHide(first, PLAYER, null, false, 5000L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void sessionSwitchStartsANewDeadlineEvenForTheSamePackage() {
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1000L));
        assertEquals(1180L, gate.retryAt());
        assertTrue(gate.shouldHide(second, PLAYER, null, false, 1100L));
        assertEquals(1280L, gate.retryAt());
        assertFalse(gate.owns(first));
        assertTrue(gate.owns(second));
        assertTrue(gate.shouldHide(second, PLAYER, null, false, 1180L));
        assertTrue(gate.shouldHide(second, PLAYER, null, false, 1279L));
        assertFalse(gate.shouldHide(second, PLAYER, null, false, 1280L));
        assertEquals(-1L, gate.retryAt());
    }

    @Test
    public void matchingAppRevokesAdmissionSoLaterUnknownSnapshotGetsAFreshDeadline() {
        assertFalse(gate.shouldHide(first, PLAYER, OTHER, true, 1000L));
        assertTrue(gate.shouldHide(first, PLAYER, PLAYER, false, 1100L));
        assertEquals(-1L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1110L));
        assertEquals(1290L, gate.retryAt());
    }

    @Test
    public void clearDropsOwnershipPendingDeadlineAndPreviousAdmission() {
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1000L));
        gate.clear();
        assertFalse(gate.owns(first));
        assertEquals(-1L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 1100L));
        assertEquals(1280L, gate.retryAt());
        assertFalse(gate.shouldHide(first, PLAYER, null, false, 1280L));
        gate.clear();
        assertFalse(gate.owns(first));
        assertEquals(-1L, gate.retryAt());
        assertTrue(gate.shouldHide(first, PLAYER, null, false, 2000L));
        assertEquals(2180L, gate.retryAt());
    }
}
