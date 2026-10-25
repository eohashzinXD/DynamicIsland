package dev.android.dynamicisland.island.view.media;

import static org.junit.Assert.*;

import dev.android.dynamicisland.island.graphics.ArtworkPalette;
import dev.android.dynamicisland.island.model.IslandItem;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MediaFxPaletteTest {
    private IslandItem item(ArtworkPalette palette) {
        IslandItem item = new IslandItem("media:same-key");
        item.isMedia = true;
        item.playing = false;
        item.accent = palette.primary;
        item.mediaPalette = palette;
        return item;
    }

    @Test public void pausedSecondaryOnlyChangeKeepsFramesRunningButLeavesGlowAccentAlone() {
        ArtworkPalette old = WaveformPaletteTransitionTest.album(false);
        ArtworkPalette next = WaveformPaletteTransitionTest.album(true);
        assertEquals(old.primary, next.primary);
        MediaFx fx = new MediaFx(null); // no art: no owner/resources needed
        fx.sync(item(old), 1000);
        assertFalse(fx.animating(2000));
        fx.sync(item(next), 2000);
        assertEquals(old.primary, fx.accent(2250));
        assertEquals(old.last, fx.waveformColorAt(1f, 2000));
        assertTrue("paused secondary-only fade must request frames", fx.animating(2499));
        assertEquals(next.last, fx.waveformColorAt(1f, 2500));
        assertFalse(fx.animating(2500));
    }

    @Test public void staleItemCannotRetargetWaveformOrScalarAccent() {
        ArtworkPalette old = WaveformPaletteTransitionTest.album(false);
        ArtworkPalette next = WaveformPaletteTransitionTest.album(true);
        IslandItem stale = item(ArtworkPalette.solid(0xFF00FFFF));
        MediaFx fx = new MediaFx(null);
        fx.sync(item(old), 1000);
        fx.sync(item(next), 2000);
        int current = fx.waveformColorAt(.25f, 2250);
        fx.sync(stale, 2250);
        assertEquals(current, fx.waveformColorAt(.25f, 2250));
        assertEquals(next.primary, fx.accent(2500));
        assertEquals(next.colorAt(.25f), fx.waveformColorAt(.25f, 2500));
    }

    @Test public void missingPaletteUsesAccentAndCrossfadesBackToSolid() {
        ArtworkPalette palette = WaveformPaletteTransitionTest.album(false);
        MediaFx fx = new MediaFx(null);
        IslandItem initial = item(palette);
        initial.mediaPalette = null;
        fx.sync(initial, 1000);
        assertEquals(initial.accent, fx.waveformColorAt(.5f, 1000));
        fx.sync(item(palette), 2000);
        assertEquals(palette.middle, fx.waveformColorAt(.5f, 2500));
        IslandItem missing = item(palette);
        missing.mediaPalette = null;
        fx.sync(missing, 3000);
        assertEquals(palette.middle, fx.waveformColorAt(.5f, 3000));
        assertEquals(palette.primary, fx.waveformColorAt(.5f, 3500));
    }
}
