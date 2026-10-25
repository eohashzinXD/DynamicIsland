package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.graphics.Canvas;
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
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "mdpi")
public class MaterialContentMotionTest {
    private void verify(int fps, boolean interrupt) {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(600, 300, 44, 22, false);
        view.layout(0, 0, 600, 900);
        IslandItem item = new IslandItem("live:test");
        item.title = "A readable card";
        view.setItems(List.of(item));
        view.peek(item, 60000);
        Shapes shapes = ReflectionHelpers.getField(view, "shapes");
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        long dt = Math.round(1_000_000_000d / fps);
        Card card = view.cards().get(0);
        boolean blurred = false;
        for (int i = 0; i < (interrupt ? Math.round(fps * .08f) : 2 * fps); i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
            float blur = StackPainter.contentBlurAmount(card, SystemClock.uptimeMillis(), shapes.expansion(card));
            assertTrue(blur >= 0 && blur <= 1);
            blurred |= blur > .1f;
        }
        assertTrue("opening must blur rather than only fade", blurred);
        float before = StackPainter.contentBlurAmount(card, SystemClock.uptimeMillis(), shapes.expansion(card));
        if (!interrupt) assertEquals("settled text must be crisp", 0, before, .001f);
        view.collapse();
        assertEquals("interrupting expansion must preserve its current blur", before,
                StackPainter.contentBlurAmount(card, SystemClock.uptimeMillis(), shapes.expansion(card)), .001f);
        float previous = before;
        boolean closingBlurred = false;
        for (int i = 0; i < fps; i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt));
            view.doFrame(nanos += dt);
            if (!view.cards().contains(card)) break;
            float blur = StackPainter.contentBlurAmount(card, SystemClock.uptimeMillis(), shapes.expansion(card));
            assertTrue("closing blur must never jump back to sharp", blur >= previous - .001f);
            if (StackPainter.contentAlpha(card, SystemClock.uptimeMillis(), shapes.expansion(card)) > .05f) closingBlurred |= blur > .05f;
            previous = blur;
        }
        assertTrue("blur must be visible while content is still present", closingBlurred);
    }
    @Test public void expandAndFoldAt60Hz() { verify(60, false); }
    @Test public void expandAndFoldAt120Hz() { verify(120, false); }
    @Test public void rapidReversalPreservesBlurAt120Hz() { verify(120, true); }

    @Test public void compactTransitionBlurSettlesAndSurvivesContentUpdatesAndReversal() {
        Content content = new Content(null, new IslandItem("sample")) {
            @Override float desiredWidth() { return 100; }
            @Override void draw(Canvas canvas, RectF bounds, float alpha, long elapsed) { }
        };
        Layer ordinary = Layer.incoming(content, 1000, 0);
        assertEquals("ordinary text updates do not opt into the expansion effect", 0, ordinary.blurAmount(1020), 0);
        Layer incoming = Layer.incoming(content, 1000, 80).materialTransition();
        assertEquals(1, incoming.blurAmount(1080), 0);
        float middle = incoming.blurAmount(1210);
        assertTrue(middle > 0 && middle < 1);
        assertEquals(middle, Layer.shownLike(content, incoming).blurAmount(1210), 0);
        Layer outgoing = incoming.toOutgoing(1210, 0);
        assertEquals(middle, outgoing.blurAmount(1210), .001f);
        assertTrue(outgoing.blurAmount(1240) > middle);
        assertEquals(1, outgoing.blurAmount(1320), 0);
        assertEquals(0, incoming.blurAmount(1340), 0);
    }
    @Test public void complementaryWeightsStayContinuousWhenDirectionReverses() {
        ContentHandoff opening = new ContentHandoff(null, true, 1000);
        for (int time = 1000; time <= 1220; time++) {
            assertEquals(1, opening.expanded(time) + opening.compact(time), .00001f);
        }
        ContentHandoff closing = new ContentHandoff(opening, false, 1090);
        assertEquals(opening.expanded(1090), closing.expanded(1090), .00001f);
        assertEquals(opening.compact(1090), closing.compact(1090), .00001f);
        assertEquals(0, closing.expanded(1400), 0);
        assertEquals(1, closing.compact(1400), 0);
    }

}
