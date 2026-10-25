package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

/** Samsung's real backdrop is separate from Canvas opacity, unlike the previous headless card films. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FoldBackdropTest {
    static class Sink implements BlurSink {
        final Map<Object, Float> backdrops = new HashMap<>();
        @Override public boolean available() { return true; }
        @Override public void beginFrame() { backdrops.clear(); }
        @Override public void endFrame() { }
        @Override public void place(Object key, RectF bounds, float corner, float alpha) { backdrops.put(key, 1f); }
        @Override public void place(Object key, RectF bounds, float corner, float alpha, float backdrop) { backdrops.put(key, backdrop); }
        @Override public void release(Object key) { backdrops.remove(key); }
        @Override public void setPainter(CardPainter painter) { }
    }
    @After public void reset() { ReflectionHelpers.setStaticField(ModuleSettings.class, "legacyAnimation", false); }

    private void verify(int fps, boolean legacy) { verify(fps, legacy, 2 * fps, false); }

    private void verify(int fps, boolean legacy, int openFrames, boolean multiple) {
        ReflectionHelpers.setStaticField(ModuleSettings.class, "legacyAnimation", legacy);
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(600, 300, 44, 22, false);
        view.layout(0, 0, 600, 900);
        Sink sink = new Sink();
        view.setBlurSink(sink);
        IslandItem item = new IslandItem("oa:timer");
        item.title = "A running timer";
        item.subtitle = "Live activity";
        view.setItems(multiple ? List.of(item, new IslandItem("oa:second")) : List.of(item));
        if (multiple) {
            Director director = ReflectionHelpers.getField(view, "director");
            director.expandFromPill();
        } else view.peek(item, 60000);
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        long dt = Math.round(1_000_000_000d / fps);
        for (int i = 0; i < openFrames; i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
        }
        Card card = view.cards().get(0);
        assertTrue(sink.backdrops.containsKey(card));
        if (openFrames == 2 * fps) assertEquals(1, sink.backdrops.get(card), 0.001f);
        Bitmap bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF rect = new RectF();
        view.collapse();
        boolean checkedLateFold = false;
        for (int i = 0; i < fps; i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
            assertTrue("no native slot may remain, including a staggered card", sink.backdrops.isEmpty());
            bitmap.eraseColor(Color.TRANSPARENT);
            view.draw(canvas);
            for (Card folding : view.cards()) {
                view.computeCard(folding, rect);
                int x = Math.round(rect.centerX()), y = Math.round(rect.bottom - 5);
                if (y < 0 || y >= bitmap.getHeight()) continue;
                int pixel = bitmap.getPixel(x, y);
                assertEquals("every folding frame must cover the wallpaper", 255, Color.alpha(pixel));
            }
            if (!view.cards().contains(card)) continue;
            float span = card.leaveFromH - card.targetH;
            if (card.launched && span > 1 && (card.leaveFromH - card.h.value()) / span >= 0.65f) {
                checkedLateFold = true;
            }
        }
        assertTrue("the regression must sample a late-fold frame", checkedLateFold);
    }
    /** A folding card must have no independent native slot, even before its staggered launch. */
    @Test public void firstClosingFramePaintsAnOpaqueCardInTheIslandInsteadOfTheNativeSlot() {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(600, 300, 44, 22, false);
        view.layout(0, 0, 600, 900);
        Sink sink = new Sink();
        view.setBlurSink(sink);
        IslandItem item = new IslandItem("oa:fold");
        item.title = "Running";
        view.setItems(List.of(item));
        view.peek(item, 60000);
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        for (int i = 0; i < 120; i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(16));
            view.doFrame(nanos += 16_000_000L);
        }
        Card card = view.cards().get(0);
        assertTrue(sink.backdrops.containsKey(card));
        view.collapse();
        ShadowSystemClock.advanceBy(Duration.ofMillis(16));
        view.doFrame(nanos += 16_000_000L);
        assertFalse("no separate card/blur RenderNode may travel into the pill", sink.backdrops.containsKey(card));
        RectF rect = new RectF();
        view.computeCard(card, rect);
        Bitmap bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int pixel = bitmap.getPixel(Math.round(rect.centerX()), Math.round(rect.bottom - 8));
        assertEquals("folding background must cover the wallpaper from the first frame", 255, Color.alpha(pixel));
        assertTrue("the surface folds as dark material, never translucent grey", Color.red(pixel) < 24);
    }
    @Test public void closingAStackRemovesAllSlotsIncludingTheStaggeredCard() { verify(60, false, 120, true); }
    @Test public void closingDuringOpeningAlsoPaintsOpaqueFrames() { verify(120, false, 22, true); }
    @Test public void closingBackdropIsGoneBeforeHandoffAt60Hz() { verify(60, false); }
    @Test public void closingBackdropIsGoneBeforeHandoffAt120Hz() { verify(120, false); }
    @Test public void legacyClosingAlsoRemovesTheBackdropBeforeHandoff() { verify(60, true); }
}
