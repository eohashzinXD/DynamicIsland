package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import dev.android.dynamicisland.island.graphics.ArtworkPalette;
import dev.android.dynamicisland.island.model.IslandItem;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Kept with the dedicated media tests; view package grants access to the real compact renderer. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class WaveformRenderTest {
    @Test public void actualFourBarsUseOneGradientAndNeverTouchSharedFill() {
        Metrics metrics = new Metrics(2f);
        Waveform waveform = new Waveform(metrics);
        Paints paints = new Paints(RuntimeEnvironment.getApplication().getResources().getDisplayMetrics(), 1f);
        Shader sentinel = new LinearGradient(0, 0, 10, 0, 0xFFFF0000, 0xFF0000FF, Shader.TileMode.CLAMP);
        paints.fill.setShader(sentinel);
        paints.fill.setColor(0xFF123456);
        paints.fill.setAlpha(73);
        int original = paints.fill.getColor();
        MediaFxCache cache = new MediaFxCache(null);
        IconPainter icons = new IconPainter(metrics, paints, waveform, null, cache, new RectF(), new Path());
        IslandItem media = new IslandItem("media:gradient");
        media.isMedia = true;
        media.trailing = IslandItem.Trailing.WAVEFORM;
        media.mediaPalette = ArtworkPalette.fromPixels(new int[] {
                0xFF8A85D9, 0xFF8A85D9, 0xFF8A85D9, 0xFF9A91D9, 0xFFC58FD9});
        media.accent = media.mediaPalette.primary;
        Bitmap image = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        float width = waveform.width(), left = 150f - width;
        icons.drawTrailing(canvas, media, null, 150f, 30f, width, .5f);
        int first = 0, last = 0;
        for (int bar = 0; bar < 4; bar++) {
            int x = (int) (left + bar * (metrics.waveBarW + metrics.waveGap) + metrics.waveBarW / 2f);
            int pixel = image.getPixel(x, 30);
            assertEquals(128, pixel >>> 24);
            int expected = cache.peek(media).waveformColorAt((x + .5f - left) / width, IslandView.now());
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs(((pixel >> shift) & 255) - ((expected >> shift) & 255)) <= 4);
            }
            if (bar == 0) first = pixel;
            if (bar == 3) last = pixel;
            if (bar < 3) {
                int gap = (int) (left + bar * (metrics.waveBarW + metrics.waveGap) + metrics.waveBarW + metrics.waveGap / 2);
                assertEquals(0, image.getPixel(gap, 30));
            }
        }
        assertNotEquals("the complete waveform must not be flat", first, last);
        IslandItem nonMedia = new IslandItem("recorder:flat");
        nonMedia.trailing = IslandItem.Trailing.WAVEFORM;
        nonMedia.accent = 0xFF44CC88;
        icons.drawTrailing(canvas, nonMedia, null, 150f, 80f, width, .25f);
        for (int bar = 0; bar < 4; bar++) {
            int x = (int) (left + bar * (metrics.waveBarW + metrics.waveGap) + metrics.waveBarW / 2f);
            int pixel = image.getPixel(x, 80);
            assertEquals(64, pixel >>> 24);
            assertTrue(Math.abs(((pixel >> 16) & 255) - 0x44) <= 2);
            assertTrue(Math.abs(((pixel >> 8) & 255) - 0xCC) <= 2);
            assertTrue(Math.abs((pixel & 255) - 0x88) <= 2);
        }
        assertSame(sentinel, paints.fill.getShader());
        assertEquals(original, paints.fill.getColor());
        assertEquals(73, paints.fill.getAlpha());
    }
}
