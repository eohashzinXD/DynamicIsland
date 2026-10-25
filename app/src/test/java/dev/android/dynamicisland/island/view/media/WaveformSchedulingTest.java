package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.os.SystemClock;
import dev.android.dynamicisland.island.graphics.ArtworkPalette;
import dev.android.dynamicisland.island.model.IslandItem;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

/** Exercises the actual IslandView pre-draw animation decision, without requiring native pixels. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WaveformSchedulingTest {
    private ArtworkPalette album(boolean alternate) {
        int[] pixels = new int[120];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = i < 80 ? 0xFFCC2020 : i < 100
                    ? (alternate ? 0xFFCC9420 : 0xFFCC8420)
                    : (alternate ? 0xFFCC2094 : 0xFFCC2084);
        }
        return ArtworkPalette.fromPixels(pixels);
    }

    private IslandItem item(ArtworkPalette palette) {
        IslandItem item = new IslandItem("media:paused");
        item.kind = IslandItem.Kind.MEDIA;
        item.isMedia = true;
        item.playing = false;
        item.trailing = IslandItem.Trailing.WAVEFORM;
        item.accent = palette.primary;
        item.mediaPalette = palette;
        return item;
    }

    private boolean scheduled(IslandView view) throws Exception {
        Field field = IslandView.class.getDeclaredField("frameScheduled");
        field.setAccessible(true);
        return field.getBoolean(view);
    }

    private void frame(IslandView view, long deltaMs) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(deltaMs));
        view.doFrame(SystemClock.uptimeMillis() * 1_000_000L);
    }

    @Test public void stablePausedSameKeySecondaryChangeSchedulesBeforeAnyDraw() throws Exception {
        ArtworkPalette old = album(false), next = album(true);
        assertEquals(old.primary, next.primary);
        assertNotEquals(old, next);
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(1080, 540f, 66f, 90f, false);
        view.setSuppressed(false);
        view.layout(0, 0, 1080, 900);
        IslandItem first = item(old);
        view.setItems(new ArrayList<>(List.of(first)), 0, new ArrayList<>());
        for (int i = 0; i < 300; i++) frame(view, 16);
        assertFalse("paused geometry and old palette must already be settled", scheduled(view));
        view.setItems(new ArrayList<>(List.of(item(next))), 0, new ArrayList<>());
        // Deliberately DO NOT draw. This was the bug: the cache only synced during onDraw.
        frame(view, 16);
        assertTrue("palette fade must keep the frame loop alive before onDraw", scheduled(view));
        assertEquals(old.last, view.mediaFx(first).waveformColorAt(1f, IslandView.now()));
        frame(view, 250);
        assertTrue("secondary-only midpoint still needs frames", scheduled(view));
        frame(view, 250);
        for (int i = 0; i < 200; i++) frame(view, 16);
        assertFalse("static paused palette must eventually stop requesting frames", scheduled(view));
    }

    @Test public void cacheAnimatingSyncsNewPaletteBeforeDecisionAndIgnoresOldSnapshot() {
        MediaFxCache cache = new MediaFxCache(null);
        ArtworkPalette old = album(false), next = album(true);
        IslandItem first = item(old);
        assertFalse(cache.animating(first));
        ShadowSystemClock.advanceBy(Duration.ofMillis(1000));
        IslandItem second = item(next);
        assertTrue(cache.animating(second));
        ShadowSystemClock.advanceBy(Duration.ofMillis(250));
        assertTrue(cache.animating(first)); // stale snapshot must not retarget to old palette
        ShadowSystemClock.advanceBy(Duration.ofMillis(250));
        assertFalse(cache.animating(second));
        assertEquals(next.last, cache.peek(second).waveformColorAt(1f, IslandView.now()));
    }
}
