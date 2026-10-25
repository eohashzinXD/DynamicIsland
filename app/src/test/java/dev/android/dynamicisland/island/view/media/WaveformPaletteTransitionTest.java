package dev.android.dynamicisland.island.view.media;

import static org.junit.Assert.*;

import dev.android.dynamicisland.island.graphics.ArtworkPalette;
import org.junit.Test;

/** Plain JUnit: no Android runtime, bitmap, Robolectric or native libraries. */
public class WaveformPaletteTransitionTest {
    static ArtworkPalette album(boolean alternate) {
        int[] pixels = new int[120];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = i < 80 ? 0xFFCC2020 : i < 100
                    ? (alternate ? 0xFFCC9420 : 0xFFCC8420)
                    : (alternate ? 0xFFCC2094 : 0xFFCC2084);
        }
        return ArtworkPalette.fromPixels(pixels);
    }

    @Test public void firstSnapshotAndNullFallbackAreImmediateAndOpaque() {
        WaveformPaletteTransition fade = new WaveformPaletteTransition();
        fade.sync(null, 0x472468AC, 1, 1000);
        for (float p : new float[] {Float.NaN, -1f, 0f, .25f, .5f, .75f, 1f, 2f}) {
            assertEquals(ArtworkPalette.solid(0x472468AC).colorAt(p), fade.colorAt(p, 1000));
        }
        assertFalse(fade.running(1000));
    }

    @Test public void samePrimarySecondaryChangeFadesWholePaletteFor500ms() {
        ArtworkPalette old = album(false), next = album(true);
        assertEquals("fixture must preserve the primary", old.primary, next.primary);
        assertNotEquals(old, next);
        WaveformPaletteTransition fade = new WaveformPaletteTransition();
        fade.sync(old, old.primary, 1, 1000);
        fade.sync(next, next.primary, 2, 2000);
        assertTrue(fade.running(2000));
        for (float p : new float[] {0f, .5f, 1f}) {
            assertEquals(old.colorAt(p), fade.colorAt(p, 2000));
            assertEquals(mix(old.colorAt(p), next.colorAt(p), .5f), fade.colorAt(p, 2250));
            assertEquals(next.colorAt(p), fade.colorAt(p, 2500));
        }
        assertTrue(fade.running(2499));
        assertFalse(fade.running(2500));
        assertEquals(fade.colorAt(0f, 2250), fade.colorAt(-1f, 2250));
        assertEquals(fade.colorAt(1f, 2250), fade.colorAt(2f, 2250));
    }

    @Test public void interruptedFadeRestartsAtDisplayedStopsWithoutAnyJump() {
        ArtworkPalette old = album(false), next = album(true);
        WaveformPaletteTransition fade = new WaveformPaletteTransition();
        fade.sync(old, old.primary, 1, 1000);
        fade.sync(next, next.primary, 2, 2000);
        float[] positions = {0f, .12f, .25f, .5f, .71f, 1f};
        int[] displayed = new int[positions.length];
        for (int i = 0; i < positions.length; i++) displayed[i] = fade.colorAt(positions[i], 2173);
        fade.sync(null, 0xFF3366CC, 3, 2173);
        for (int i = 0; i < positions.length; i++) {
            assertEquals(displayed[i], fade.colorAt(positions[i], 2173));
            if (positions[i] == 0f || positions[i] == .5f || positions[i] == 1f) {
                assertEquals(mix(displayed[i], 0xFF3366CC, .5f), fade.colorAt(positions[i], 2423));
            }
            assertEquals(0xFF3366CC, fade.colorAt(positions[i], 2673));
        }
        assertFalse(fade.running(2673));
    }

    @Test public void staleSnapshotsAndRepeatedTargetsDoNotRestartOrRetarget() {
        ArtworkPalette old = album(false), next = album(true);
        WaveformPaletteTransition fade = new WaveformPaletteTransition();
        fade.sync(old, old.primary, 10, 1000);
        fade.sync(next, next.primary, 11, 2000);
        int midpoint = fade.colorAt(1f, 2250);
        fade.sync(null, 0xFF00FFFF, 9, 2250);
        assertEquals(midpoint, fade.colorAt(1f, 2250));
        // An equal-value palette in a distinct object, with a newer or equal seq, is still no change.
        fade.sync(album(true), next.primary, 12, 2300);
        fade.sync(next, next.primary, 12, 2400);
        assertEquals(next.last, fade.colorAt(1f, 2500));
        assertFalse(fade.running(2500));
        fade.sync(old, old.primary, 11, 2600);
        assertEquals(next.first, fade.colorAt(0f, 2600));
    }

    @Test public void fallbackChangesFadeAndSeparateItemsHaveIndependentState() {
        WaveformPaletteTransition a = new WaveformPaletteTransition();
        WaveformPaletteTransition b = new WaveformPaletteTransition();
        a.sync(null, 0xFFFF0000, 1, 0);
        b.sync(null, 0xFF00FF00, 1, 0);
        a.sync(null, 0xFF0000FF, 2, 1000);
        assertEquals(0xFF800080, a.colorAt(.3f, 1250));
        assertEquals(0xFF00FF00, b.colorAt(.3f, 1250));
        assertEquals(0xFF0000FF, a.colorAt(.3f, 1500));
    }

    private static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            out |= Math.round(((a >>> shift) & 255) * (1f - t) + ((b >>> shift) & 255) * t) << shift;
        }
        return out;
    }
}
