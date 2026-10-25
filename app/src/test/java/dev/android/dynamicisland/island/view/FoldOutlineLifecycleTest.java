package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.SystemClock;
import dev.android.dynamicisland.island.model.IslandItem;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

/** Include the delayed pill fade and regrowth that unattached-view folding tests previously skipped. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class FoldOutlineLifecycleTest {
    private void verify(int fps) {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(480, 240, 40, 22, false);
        view.layout(0, 0, 480, 600);
        view.setItems(List.of(new IslandItem("timer:a"), new IslandItem("timer:b")));
        Director director = ReflectionHelpers.getField(view, "director");
        Scene scene = ReflectionHelpers.getField(view, "scene");
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        long dt = Math.round(1_000_000_000d / fps);
        for (int i = 0; i < fps; i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
        }
        director.expandFromPill();
        for (int i = 0; i < 2 * fps; i++) {
            if (i == (int)Math.ceil(.26 * fps)) director.fadePillBlobNow();
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
        }
        assertEquals("the expanded pill must actually have faded out", 0, scene.springs.pillBlob.value(), .001f);
        view.collapse();
        Bitmap bitmap = Bitmap.createBitmap(480, 600, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF pill = new RectF();
        boolean partial = false;
        for (int i = 0; i < fps; i++) {
            if (i == (int)Math.ceil(.07 * fps)) {
                Runnable regrow = ReflectionHelpers.getField(director, "regrowPill");
                regrow.run();
            }
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
            float blob = scene.springs.pillBlob.value();
            if (blob <= .01f || blob >= .95f) continue;
            partial = true;
            bitmap.eraseColor(Color.TRANSPARENT);
            // Device cards are in a sibling BlurLayer under the island; reproduce that draw order.
            for (int c = view.cards().size() - 1; c >= 0; c--) view.drawCard(canvas, view.cards().get(c), SystemClock.uptimeMillis());
            view.draw(canvas);
            view.computeMain(pill);
            int x = Math.round(pill.centerX()), y = Math.round(pill.top + 4);
            int pixel = bitmap.getPixel(x, y);
            assertTrue("white outline leaks into the returning body at " + fps + "Hz frame " + i
                    + ", blob=" + blob + ", pixel=" + Integer.toHexString(pixel), Color.red(pixel) <= 24);
        }
        assertTrue("must sample the real returning-body fade", partial);
    }
    @Test public void returningPillDoesNotFillGreyAt60Hz() { verify(60); }
    @Test public void returningPillDoesNotFillGreyAt120Hz() { verify(120); }
}
