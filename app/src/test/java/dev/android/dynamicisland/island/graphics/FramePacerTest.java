package dev.android.dynamicisland.island.graphics;

import static org.junit.Assert.*;
import org.junit.Test;

public class FramePacerTest {
    private int rendered(boolean limited, boolean interactive, int displayHz) {
        FramePacer pacer = new FramePacer();
        int frames = 0;
        for (int i = 0; i < displayHz * 2; i++)
            if (pacer.allow(limited, interactive, 1_000_000_000L + i * (1_000_000_000L / displayHz))) frames++;
        return frames;
    }
    @Test public void highRefreshDisplaysUseThirtyOrSixtyRenderFramesPerSecond() {
        assertEquals(60, rendered(true, false, 120));
        assertEquals(120, rendered(true, true, 120));
        assertEquals(60, rendered(true, false, 60));
        assertEquals(120, rendered(true, true, 60));
        assertEquals(240, rendered(false, false, 120));
        assertEquals(180, rendered(false, true, 90));
    }
    @Test public void touchUpgradesAnAmbientBudgetWithoutWaitingForThirtyFps() {
        FramePacer pacer = new FramePacer();
        assertTrue(pacer.allow(true, false, 1_000_000_000L));
        assertFalse(pacer.allow(true, false, 1_016_666_667L));
        assertTrue(pacer.allow(true, true, 1_016_666_667L));
        assertTrue(pacer.allow(false, false, 1_017_000_000L));
    }
    @Test public void resumedSurfacesAndLongStallsAreImmediatelyEligible() {
        FramePacer pacer = new FramePacer();
        pacer.allow(true, false, 1_000_000_000L);
        assertEquals(33, pacer.delayMillis(true, false, 1_000_000_000L));
        assertEquals(16, pacer.delayMillis(true, true, 1_000_000_000L));
        assertEquals(0, pacer.delayMillis(false, false, 1_000_000_000L));
        assertEquals(0, pacer.delayMillis(true, false, 2_000_000_000L));
        assertTrue(pacer.allow(true, false, 2_000_000_000L));
        pacer.reset();
        assertTrue(pacer.allow(true, false, 2_000_000_001L));
    }
}
