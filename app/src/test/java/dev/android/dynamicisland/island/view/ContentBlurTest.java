package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.view.View;
import android.os.SystemClock;
import dev.android.dynamicisland.island.model.IslandItem;
import java.time.Duration;
import java.util.List;
import org.robolectric.shadows.ShadowSystemClock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class ContentBlurTest {
    @Test public void recordsIsolatedForegroundOnTheHardwareCanvasAndReleasesItWhenSettled() {
        ContentBlur blur = new ContentBlur();
        RenderNode root = new RenderNode("test window");
        Canvas target = root.beginRecording(400, 300);
        assertTrue(target.isHardwareAccelerated());
        RectF bounds = new RectF(40, 50, 260, 150);
        Canvas content = blur.begin(target, bounds, .6f, 20);
        assertNotSame("only content should enter the filtered node", target, content);
        content.drawCircle(80, 80, 10, new Paint());
        blur.end(target, content);
        RenderNode node = ReflectionHelpers.getField(blur, "node");
        assertTrue(node.hasDisplayList());
        assertEquals(40, node.getLeft());
        assertEquals(50, node.getTop());
        assertEquals(220, node.getWidth());
        assertEquals(100, node.getHeight());
        RenderEffect[] effects = ReflectionHelpers.getField(blur, "effects");
        RenderEffect cached = effects[12];
        assertNotNull(cached);
        content = blur.begin(target, bounds, .601f, 20);
        blur.end(target, content);
        assertSame("adjacent frames reuse the quantised Gaussian filter", cached, effects[12]);
        assertSame(target, blur.begin(target, bounds, 0, 20));
        assertFalse("an idle card must release its recorded blur buffer", node.hasDisplayList());
        assertEquals(0, (int)ReflectionHelpers.getField(blur, "nodeStep"));
        root.endRecording();
    }
    @Test public void multipleCardsCannotOverwriteEachOthersRecordedContent() {
        ContentBlur first = new ContentBlur(), second = new ContentBlur();
        RenderNode root = new RenderNode("stack");
        Canvas target = root.beginRecording(400, 600);
        Canvas content = first.begin(target, new RectF(10, 20, 300, 180), .5f, 10);
        content.drawColor(Color.RED);
        first.end(target, content);
        content = second.begin(target, new RectF(10, 200, 300, 380), .5f, 10);
        content.drawColor(Color.BLUE);
        second.end(target, content);
        assertNotSame(ReflectionHelpers.getField(first, "node"), ReflectionHelpers.getField(second, "node"));
        root.endRecording();
    }
    @Test public void softwareCaptureKeepsItsContentAndNeverAllocatesAGpuNode() {
        ContentBlur blur = new ContentBlur();
        Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        Canvas target = new Canvas(bitmap);
        Canvas content = blur.begin(target, new RectF(0, 0, 40, 40), 1, 10);
        assertSame(target, content);
        content.drawColor(Color.WHITE);
        blur.end(target, content);
        assertEquals(Color.WHITE, bitmap.getPixel(20, 20));
        assertNull(ReflectionHelpers.getField(blur, "node"));
    }
    @Test public void remoteViewEffectsAreClearedWhenHiddenAndCanBeAppliedAgain() {
        class EffectView extends View {
            RenderEffect effect;
            int calls;
            EffectView() { super(RuntimeEnvironment.getApplication()); }
            @Override public void setRenderEffect(RenderEffect effect) { this.effect = effect; calls++; super.setRenderEffect(effect); }
        }
        EffectView view = new EffectView();
        ContentBlur blur = new ContentBlur();
        blur.apply(view, .5f, 10);
        RenderEffect effect = view.effect;
        assertNotNull(effect);
        blur.apply(view, .501f, 10);
        assertEquals("steady blur does not repeatedly update the view", 1, view.calls);
        blur.clearView();
        assertNull(view.effect);
        blur.apply(view, .5f, 10);
        assertSame(effect, view.effect);
        blur.apply(view, 0, 10);
        assertNull(view.effect);
    }
    @Test public void actualCardRendererUsesTheFilteredNodeOnlyDuringTheTransition() {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(600, 300, 44, 22, false);
        view.layout(0, 0, 600, 900);
        IslandItem item = new IslandItem("live:gpu");
        item.title = "Foreground only";
        view.setItems(List.of(item));
        view.peek(item, 60000);
        Card card = view.cards().get(0);
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        for (int frame = 0; frame < 8; frame++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(16));
            view.doFrame(nanos += 16000000);
        }
        RenderNode root = new RenderNode("actual card surface");
        Canvas canvas = root.beginRecording(600, 900);
        view.drawCard(canvas, card, SystemClock.uptimeMillis());
        root.endRecording();
        RenderNode foreground = ReflectionHelpers.getField(card.contentBlur, "node");
        assertNotNull(foreground);
        assertTrue(foreground.hasDisplayList());
        for (int frame = 0; frame < 120; frame++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(16));
            view.doFrame(nanos += 16000000);
        }
        canvas = root.beginRecording(600, 900);
        view.drawCard(canvas, card, SystemClock.uptimeMillis());
        root.endRecording();
        assertFalse("the settled card must draw crisp content directly", foreground.hasDisplayList());
    }

}
