package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;
import android.graphics.RectF;
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
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

/** Samsung's real backdrop is separate from Canvas opacity, unlike the previous headless card films. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "mdpi")
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

    private void verify(int fps, boolean legacy) {
        ReflectionHelpers.setStaticField(ModuleSettings.class, "legacyAnimation", legacy);
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(600, 300, 44, 22, false);
        view.layout(0, 0, 600, 900);
        Sink sink = new Sink();
        view.setBlurSink(sink);
        IslandItem item = new IslandItem("oa:timer");
        item.title = "A running timer";
        item.subtitle = "Live activity";
        view.setItems(List.of(item));
        view.peek(item, 60000);
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        long dt = Math.round(1_000_000_000d / fps);
        for (int i = 0; i < fps * 2; i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
        }
        Card card = view.cards().get(0);
        assertEquals(1, sink.backdrops.get(card), 0.001f);
        view.collapse();
        boolean checkedLateFold = false;
        float previous = 1;
        for (int i = 0; i < fps; i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
            Float backdrop = sink.backdrops.get(card);
            if (backdrop == null) continue;
            assertTrue("the backdrop must not flash back on", backdrop >= 0 && backdrop <= previous + 0.001f);
            previous = backdrop;
            float span = card.leaveFromH - card.targetH;
            if (card.launched && span > 1 && (card.leaveFromH - card.h.value()) / span >= 0.65f) {
                assertEquals("no native grey backdrop may survive into the pill handoff", 0, backdrop, 0.001f);
                checkedLateFold = true;
            }
        }
        assertTrue("the regression must sample a late-fold frame", checkedLateFold);
    }
    @Test public void closingBackdropIsGoneBeforeHandoffAt60Hz() { verify(60, false); }
    @Test public void closingBackdropIsGoneBeforeHandoffAt120Hz() { verify(120, false); }
    @Test public void legacyClosingAlsoRemovesTheBackdropBeforeHandoff() { verify(60, true); }
}
