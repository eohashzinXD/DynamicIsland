package dev.android.dynamicisland.island.view.media;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class WaveformGradientTest {
    private static final int FIRST = 0xFF8A85D9, MIDDLE = 0xFF9A91D9, LAST = 0xFFC58FD9;

    @Test public void normalizedShaderIsReusedAcrossMotionResizeAlphaAndSolidDraws() {
        WaveformGradient cache = new WaveformGradient();
        Paint p = cache.paint(FIRST, MIDDLE, LAST, 10, 80, 1f);
        Shader shader = p.getShader();
        assertNotNull(shader);
        assertSame(p, cache.paint(FIRST, MIDDLE, LAST, 100, 160, .5f));
        assertSame(shader, p.getShader());
        assertEquals(128, p.getAlpha());
        cache.paint(FIRST, FIRST, FIRST, 0, 20, .25f);
        assertNull(p.getShader());
        assertEquals(64, p.getAlpha());
        cache.paint(FIRST, MIDDLE, LAST, 0, 20, 1f);
        assertSame(shader, p.getShader());
        cache.paint(FIRST, MIDDLE, 0xFFDD99EE, 0, 20, 1f);
        assertNotSame(shader, p.getShader());
    }

    @Test public void horizontalPixelsStayAnchoredToBoundsNotAbsoluteScreenCoordinates() {
        Bitmap bmp = Bitmap.createBitmap(300, 40, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        WaveformGradient cache = new WaveformGradient();
        canvas.drawRect(10, 0, 90, 10, cache.paint(FIRST, MIDDLE, LAST, 10, 80, 1f));
        canvas.drawRect(100, 10, 260, 20, cache.paint(FIRST, MIDDLE, LAST, 100, 160, .5f));
        // Reusing one cache at another location/size must not move the earlier rendered gradient.
        near(FIRST, bmp.getPixel(10, 5), 3);
        near(MIDDLE, bmp.getPixel(50, 5), 3);
        near(LAST, bmp.getPixel(89, 5), 3);
        assertEquals(128, bmp.getPixel(100, 15) >>> 24);
        near(0x808A85D9, bmp.getPixel(100, 15), 3);
        near(0x809A91D9, bmp.getPixel(180, 15), 3);
        near(0x80C58FD9, bmp.getPixel(259, 15), 3);
        assertEquals(0, bmp.getPixel(99, 15));
        assertEquals(0, bmp.getPixel(260, 15));
        // A different item's cache cannot change this shader or paint.
        WaveformGradient other = new WaveformGradient();
        canvas.drawRect(10, 20, 90, 30, other.paint(0xFF33CC55, 0xFF66DD88, 0xFF99EEAA, 10, 80, 1));
        canvas.drawRect(100, 30, 260, 40, cache.paint(FIRST, MIDDLE, LAST, 100, 160, 1));
        near(MIDDLE, bmp.getPixel(180, 35), 3);
        // These album-derived stops remain clearly visible against the island's black background.
        for (int x : new int[] {10, 50, 89}) {
            int pixel = bmp.getPixel(x, 5);
            assertTrue(((pixel >> 16) & 255) > 100);
            assertTrue(((pixel >> 8) & 255) > 100);
            assertTrue((pixel & 255) > 100);
        }
    }

    private static void near(int expected, int actual, int tolerance) {
        for (int shift = 0; shift <= 24; shift += 8) {
            assertTrue("expected=" + Integer.toHexString(expected) + " actual=" + Integer.toHexString(actual),
                    Math.abs(((expected >>> shift) & 255) - ((actual >>> shift) & 255)) <= tolerance);
        }
    }
}
