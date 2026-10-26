package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;
import org.junit.Test;

public class PillOutlinePolicyTest {
    private static final int BLACK = 0xFF000000;
    private final PillOutlinePolicy policy = new PillOutlinePolicy();

    @Test public void automaticDefaultsToVisibleWhenBackgroundIsUnknown() {
        assertTrue(policy.visible(0, BLACK, BLACK, false));
        assertFalse(policy.visible(2, BLACK, BLACK, false));
    }
    @Test public void blackOnBlackShowsAndBlackOnWhiteHides() {
        policy.sample(0f);
        assertTrue(policy.visible(0, BLACK, BLACK, false));
        policy.sample(1f);
        assertFalse(policy.visible(0, BLACK, BLACK, false));
        policy.sample(0f);
        assertTrue(policy.visible(0, BLACK, BLACK, false));
    }
    @Test public void manualChoicesOverrideAllSamples() {
        for (Float luma : new Float[]{null, 0f, .5f, 1f}) {
            policy.sample(luma);
            assertTrue(policy.visible(1, BLACK, BLACK, true));
            assertFalse(policy.visible(2, BLACK, BLACK, true));
        }
    }
    @Test public void hysteresisPreventsBorderlineSampleFlicker() {
        policy.sample(.17f);
        assertFalse(policy.visible(0, BLACK, BLACK, false));
        policy.sample(.12f);
        assertFalse(policy.visible(0, BLACK, BLACK, false));
        policy.sample(.08f);
        assertTrue(policy.visible(0, BLACK, BLACK, false));
        policy.sample(.12f);
        assertTrue(policy.visible(0, BLACK, BLACK, false));
    }
    @Test public void coloredPillAndBubbleNeedContrastAgainstTheSameRealBackground() {
        int grey = 0xFF777777;
        policy.sample(PillOutlinePolicy.luma(grey));
        assertTrue(policy.visible(0, grey, BLACK, false));
        policy.sample(.5f);
        assertFalse(policy.visible(0, BLACK, grey, false));
        assertTrue(policy.visible(0, BLACK, grey, true));
    }
    @Test public void invalidSamplesRestoreConservativeOutline() {
        for (Float value : new Float[]{null, Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f}) {
            policy.sample(1f);
            assertFalse(policy.visible(0, BLACK, BLACK, false));
            policy.sample(value);
            assertTrue(policy.visible(0, BLACK, BLACK, false));
        }
    }
}
