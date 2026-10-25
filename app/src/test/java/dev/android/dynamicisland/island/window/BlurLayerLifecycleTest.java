package dev.android.dynamicisland.island.window;

import static org.junit.Assert.*;
import android.graphics.Outline;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Test separate backdrop/surface RenderNodes even where Samsung's native blur API is unavailable. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class BlurLayerLifecycleTest {
    static class Backend implements BlurLayer.Backend {
        int applied, cleared;
        View target;
        @Override public boolean available() { return true; }
        @Override public void apply(View view, int radius, float corner, int tint) { applied++; target = view; }
        @Override public void clear(View view) { cleared++; }
        @Override public void disable() { }
    }
    @Test public void fadingTheBackdropKeepsTheCardSurfaceOpaqueWithoutRebuildingNativeBlur() {
        Backend backend = new Backend();
        BlurLayer layer = new BlurLayer(RuntimeEnvironment.getApplication(), backend);
        Object owner = new Object();
        layer.layout(0, 0, 600, 400);
        int[] painted = {0};
        layer.setPainter((key, canvas, left, top) -> {
            assertSame(owner, key);
            assertEquals(90, left, 0);
            assertEquals(20, top, 0);
            painted[0]++;
            canvas.drawColor(Color.BLACK);
        });
        RectF rect = new RectF(90, 20, 310, 140);
        layer.beginFrame(); layer.place(owner, rect, 36, 1, 0.8f); layer.endFrame();
        FrameLayout slot = (FrameLayout) layer.getChildAt(0);
        View backdrop = slot.getChildAt(0), surface = slot.getChildAt(1);
        assertSame(backdrop, backend.target);
        assertEquals(1, surface.getAlpha(), 0);
        assertEquals(1, slot.getAlpha(), 0);
        layer.beginFrame(); layer.place(owner, rect, 36, 1, 0.2f); layer.endFrame();
        assertEquals(0.2f, backdrop.getAlpha(), 0.001f);
        assertEquals(1, surface.getAlpha(), 0);
        assertEquals(1, backend.applied);
        assertTrue(backdrop.getClipToOutline());
        Outline outline = new Outline();
        backdrop.getOutlineProvider().getOutline(backdrop, outline);
        Rect clip = new Rect();
        assertTrue(outline.getRect(clip));
        assertEquals(new Rect(0, 0, 220, 120), clip);
        layer.beginFrame(); layer.place(owner, rect, 36, 1, 0); layer.endFrame();
        assertEquals(View.GONE, backdrop.getVisibility());
        assertEquals(1, backend.cleared);
        assertEquals(View.VISIBLE, slot.getVisibility());
        assertEquals(1, surface.getAlpha(), 0);
        Bitmap bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888);
        layer.draw(new Canvas(bitmap));
        assertTrue(painted[0] > 0);
        assertEquals("the foreground still paints after the native backdrop is gone", Color.BLACK, bitmap.getPixel(120, 50));
    }
    @Test public void releaseClearsTheBackdropImmediatelyEvenWhileItsPaintedSurfaceLingers() {
        Backend backend = new Backend();
        BlurLayer layer = new BlurLayer(RuntimeEnvironment.getApplication(), backend);
        Object owner = new Object();
        layer.beginFrame(); layer.place(owner, new RectF(80, 10, 300, 70), 30, 1, 1); layer.endFrame();
        FrameLayout slot = (FrameLayout) layer.getChildAt(0);
        layer.release(owner);
        assertEquals(1, backend.cleared);
        assertEquals(View.GONE, slot.getChildAt(0).getVisibility());
        assertEquals(0, slot.getChildAt(0).getAlpha(), 0);
        assertEquals(1, slot.getChildAt(1).getAlpha(), 0);
    }
    @Test public void aMissedPlacementNeverKeepsAStaleNativeBackdrop() {
        Backend backend = new Backend();
        BlurLayer layer = new BlurLayer(RuntimeEnvironment.getApplication(), backend);
        layer.beginFrame(); layer.place(new Object(), new RectF(80, 10, 300, 70), 30, 1, 1); layer.endFrame();
        FrameLayout slot = (FrameLayout) layer.getChildAt(0);
        layer.beginFrame(); layer.endFrame();
        assertEquals(View.GONE, slot.getChildAt(0).getVisibility());
        assertEquals(View.VISIBLE, slot.getVisibility());
        layer.beginFrame(); layer.endFrame();
        assertEquals(View.GONE, slot.getVisibility());
    }
}
