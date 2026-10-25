package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.SystemClock;
import android.view.MotionEvent;
import dev.android.dynamicisland.island.feedback.AudioLevels;
import dev.android.dynamicisland.island.model.IslandItem;
import java.time.Duration;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Measure the painted body, rather than merely testing the spring that the painter used to clamp. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class ShadeBounceTest {
    private final Application app = RuntimeEnvironment.getApplication();

    private final class Film {
        final IslandView view;
        final Bitmap bitmap = Bitmap.createBitmap(600, 160, Bitmap.Config.ARGB_8888);
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        final int fps;

        Film(int fps) {
            this(fps, IslandHost.NONE);
        }

        Film(int fps, IslandHost host) {
            this.fps = fps;
            view = new IslandView(app, host);
            view.setGeometry(600, 300, 60, 22, false);
            view.layout(0, 0, 600, 160);
            IslandItem item = new IslandItem("motion");
            item.kind = IslandItem.Kind.EVENT;
            item.trailing = IslandItem.Trailing.TEXT;
            item.trailingText = "Live activity";
            view.setItems(Collections.singletonList(item), 0, Collections.emptyList());
            step(fps * 2);
        }

        void step(int frames) {
            for (int i = 0; i < frames; i++) {
                long dt = Math.round(1_000_000_000d / fps);
                ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
                nanos += dt;
                view.doFrame(nanos);
            }
        }

        int paintedWidth() {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            view.draw(canvas);
            int first = 600, last = -1;
            for (int x = 0; x < 600; x++) {
                int pixel = bitmap.getPixel(x, 60);
                if (Color.red(pixel) < 24 && Color.green(pixel) < 24 && Color.blue(pixel) < 24) {
                    first = Math.min(first, x);
                    last = Math.max(last, x);
                }
            }
            return last < 0 ? 0 : last - first + 1;
        }
    }

    private void assertBounce(int fps) {
        Film film = new Film(fps);
        int resting = film.paintedWidth();
        film.view.setShadeFraction(0.5f);
        film.step(fps);
        assertEquals(0, film.paintedWidth());
        film.view.setShadeFraction(0);
        int peak = 0;
        float peakAt = 0;
        for (int i = 0; i < fps; i++) {
            film.step(1);
            int width = film.paintedWidth();
            if (width > peak) {
                peak = width;
                peakAt = (i + 1f) / fps;
            }
            assertTrue(film.view.visibleAlpha() >= 0 && film.view.visibleAlpha() <= 1);
            if (width > resting + 2) {
                RectF coverage = new RectF();
                film.view.getCoverageBounds(coverage);
                Region touch = new Region();
                film.view.getTouchRegion(touch);
                int right = (600 + width) / 2 - 1;
                assertTrue("status-bar coverage contains the painted bounce", coverage.right >= right);
                assertTrue("the growing edge remains touchable", touch.contains(right, 60));
            }
        }
        assertTrue("a visible overshoot is required: rest=" + resting + " peak=" + peak, peak >= resting + 3);
        assertTrue("one restrained bounce", peak < resting * 1.15f);
        assertTrue("return should have time to breathe: peak at " + peakAt, peakAt >= 0.25f && peakAt < 0.5f);
        assertEquals(resting, film.paintedWidth(), 1);
    }

    @Test public void returnHasAVisibleBounceAt60Hz() { assertBounce(60); }
    @Test public void returnHasAVisibleBounceAt120Hz() { assertBounce(120); }

    /** A successful surface transaction bypasses the painter's opacity, as it does in SystemUI. */
    private static final class SurfaceHost implements IslandHost {
        float alpha = 1;

        @Override public boolean onShadeFade(float alpha, float scale, float x, float y) {
            this.alpha = alpha;
            return true;
        }
        @Override public AudioLevels audio() { return null; }
        @Override public boolean hasMinimized() { return false; }
        @Override public void onCycle(int direction) { }
        @Override public void onExpandedChanged(boolean expanded) { }
        @Override public void onIslandFrame() { }
        @Override public void onItemAction(IslandItem item, IslandItem.Action action) { }
        @Override public void onMinimize(IslandItem item) { }
        @Override public void onOpenItem(IslandItem item, RectF from) { }
        @Override public void onRestore() { }
        @Override public void onShadeDragEvent(MotionEvent event) { }
        @Override public void onShadeDragStart(float x, float y, long time) { }
        @Override public void onWindowHeightNeeded(int height) { }
    }

    private void assertFastPull(int fps, boolean surfaceFade) {
        SurfaceHost host = surfaceFade ? new SurfaceHost() : null;
        Film film = new Film(fps, host == null ? IslandHost.NONE : host);
        RectF bounds = new RectF();
        film.view.computeMain(bounds);
        float restingWidth = bounds.width();
        film.view.setShadeFraction(1); // A fling can skip every intermediate expansion callback.
        film.view.computeMain(bounds);
        assertEquals("full expansion must not snap the body away", restingWidth, bounds.width(), 0.01f);
        assertEquals(1, host == null ? film.view.visibleAlpha() : host.alpha, 0.001f);
        float previousWidth = restingWidth, previousAlpha = 1;
        int shrinkingFrames = 0, fadingFrames = 0;
        for (int i = 0; i < fps; i++) {
            // Repeated fully-expanded callbacks must not cancel or restart the exit either.
            film.view.setShadeFraction(1);
            film.step(1);
            film.view.computeMain(bounds);
            float alpha = host == null ? film.view.visibleAlpha() : host.alpha;
            assertTrue(bounds.width() <= previousWidth + 0.01f);
            assertTrue(alpha >= 0 && alpha <= previousAlpha + 0.001f);
            if (bounds.width() < previousWidth - 0.01f && alpha > 0.99f) shrinkingFrames++;
            if (alpha > 0.01f && alpha < 0.99f) {
                fadingFrames++;
                assertTrue("fade only after shrinking near the dot", bounds.width() < restingWidth * 0.3f);
            }
            previousWidth = bounds.width();
            previousAlpha = alpha;
        }
        assertTrue("body shrinks over several visible frames", shrinkingFrames >= 3);
        assertTrue("the dot fades over several frames", fadingFrames >= 3);
        assertTrue(previousAlpha < 0.003f);
        assertTrue(film.view.isFullyHidden());
        film.view.setShadeFraction(0);
        film.step(fps);
        assertEquals(1, host == null ? film.view.visibleAlpha() : host.alpha, 0.001f);
        assertEquals(restingWidth, film.paintedWidth(), 2);
    }

    @Test public void fullExpansionKeepsAnimatingAt60Hz() { assertFastPull(60, false); }
    @Test public void fullExpansionKeepsAnimatingAt120Hz() { assertFastPull(120, false); }
    @Test public void fullExpansionKeepsSurfaceFadeAnimatingAt60Hz() { assertFastPull(60, true); }
    @Test public void fullExpansionKeepsSurfaceFadeAnimatingAt120Hz() { assertFastPull(120, true); }

    @Test
    public void reopeningTheShadeMidReturnStillHidesAndThenRestoresTheIsland() {
        Film film = new Film(120);
        int resting = film.paintedWidth();
        film.view.setShadeFraction(0.5f);
        film.step(120);
        film.view.setShadeFraction(0);
        film.step(15);
        film.view.setShadeFraction(0.5f);
        film.step(120);
        assertEquals(0, film.paintedWidth());
        film.view.setShadeFraction(0);
        film.step(120);
        assertEquals(resting, film.paintedWidth(), 1);
    }

    @Test
    public void aPillAtTheScreenEdgeDoesNotGainExtraClippingFromTheBounce() {
        Film film = new Film(60);
        film.view.setGeometry(600, 104, 60, 22, false);
        film.step(120);
        RectF resting = new RectF();
        film.view.computeMain(resting);
        assertTrue(resting.left >= 0);
        film.view.setShadeFraction(0.5f);
        film.step(60);
        film.view.setShadeFraction(0);
        for (int i = 0; i < 60; i++) {
            film.step(1);
            RectF current = new RectF();
            film.view.computeMain(current);
            assertTrue(current.left >= 0 && current.right <= 600);
            assertTrue(current.width() > 0 && current.height() > 0);
        }
    }
}
