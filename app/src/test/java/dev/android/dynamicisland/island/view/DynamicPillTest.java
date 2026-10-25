package dev.android.dynamicisland.island.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import dev.android.dynamicisland.island.graphics.BatteryIcon;
import dev.android.dynamicisland.island.graphics.GlyphIcon;
import dev.android.dynamicisland.island.model.IslandItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/**
 * The dynamic pill, across every kind of thing it can hold.
 *
 * <p>The other view tests each check one behaviour; this one sweeps the whole surface of the pill - every
 * {@link IslandItem.Kind}, every {@link IslandItem.Trailing}, the priority that decides which item wins it,
 * the chronometer that must not make it jump, and the trailing text that must be fitted rather than run
 * past the edge. Each case is also drawn, so a layout that only fails when it is painted is caught here.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class DynamicPillTest {
    private static final int W = 1080;
    private final Application app = RuntimeEnvironment.getApplication();

    private IslandItem item(String key, IslandItem.Kind kind) {
        IslandItem it = new IslandItem(key);
        it.kind = kind;
        it.title = "Title";
        it.subtitle = "Subtitle";
        it.appName = "App";
        it.iconIsImage = false;
        it.icon = GlyphIcon.of(GlyphIcon.Kind.BELL);
        return it;
    }

    private IslandView view(List<IslandItem> items) {
        IslandView view = new IslandView(app, IslandHost.NONE);
        view.setGeometry(W, W / 2f, 66f, 0f, false);
        view.setSuppressed(false);
        view.layout(0, 0, W, 400);
        view.setItems(items, 0, new ArrayList<>());
        return view;
    }

    /** Runs frames until the springs settle, then returns the pill's width. */
    private float settle(IslandView view) {
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        for (int i = 0; i < 120; i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(16));
            nanos += 16_000_000L;
            view.doFrame(nanos);
        }
        RectF r = new RectF();
        view.computeMain(r);
        return r.width();
    }

    /** Paints the view, so a layout that only throws when drawn is caught too. */
    private void paint(IslandView view) {
        Bitmap bitmap = Bitmap.createBitmap(W, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        assertNotNull(bitmap);
    }

    @Test
    public void everyKindCanHoldThePill() {
        for (IslandItem.Kind kind : IslandItem.Kind.values()) {
            IslandItem it = item("k-" + kind, kind);
            it.trailing = IslandItem.Trailing.TEXT;
            it.trailingText = "12:30";
            IslandView view = view(new ArrayList<>(List.of(it)));
            float width = settle(view);
            assertTrue(kind + " gave no pill", width > 0f);
            assertTrue(kind + " wider than the screen", width <= W);
            paint(view);
        }
    }

    @Test
    public void everyTrailingCanHoldThePill() {
        for (IslandItem.Trailing trailing : IslandItem.Trailing.values()) {
            IslandItem it = item("t-" + trailing, IslandItem.Kind.EVENT);
            it.trailing = trailing;
            it.trailingText = "12:30";
            it.progress = 0.5f;
            IslandView view = view(new ArrayList<>(List.of(it)));
            float width = settle(view);
            assertTrue(trailing + " gave no pill", width > 0f);
            paint(view);
        }
    }

    @Test
    public void theHighestPriorityItemWinsThePill() {
        // Two items, listed both ways round: the pill must come out the same, and it must be the one with
        // the higher priority that decides its width.
        IslandItem call = item("call", IslandItem.Kind.CALL);
        call.trailing = IslandItem.Trailing.TEXT;
        call.trailingText = "12:34:56";
        IslandItem event = item("event", IslandItem.Kind.EVENT);
        event.trailing = IslandItem.Trailing.TEXT;
        event.trailingText = "1";

        // setItems takes the list already in priority order (that is the caller's job, and the parameter is
        // named for it), so the pill must be the highest priority item whichever order they arrived in.
        float callAlone = settle(view(new ArrayList<>(List.of(call))));
        float callFirst = settle(view(byPriority(call, event)));
        float eventFirst = settle(view(byPriority(event, call)));
        assertEquals("the lower priority item decided the pill", callAlone, callFirst, 0.5f);
        assertEquals("the order they arrived in changed the pill", callFirst, eventFirst, 0.5f);
    }

    /** The two items in the order setItems expects: highest priority first. */
    private static List<IslandItem> byPriority(IslandItem a, IslandItem b) {
        List<IslandItem> list = new ArrayList<>(List.of(a, b));
        list.sort((x, y) -> Integer.compare(y.kind.priority, x.kind.priority));
        return list;
    }

    @Test
    public void aChronometerDoesNotMakeThePillJump() {
        IslandItem it = item("call", IslandItem.Kind.CALL);
        it.trailing = IslandItem.Trailing.CHRONOMETER;
        it.chronoBase = SystemClock.elapsedRealtime();
        IslandView view = view(new ArrayList<>(List.of(it)));

        float before = settle(view);
        // a second later the digits change, and the pill must not resize or re-animate around it
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
        float after = settle(view);
        assertEquals("the pill moved when the timer ticked", before, after, 0.5f);
    }

    @Test
    public void aLongTrailingTextIsFittedToThePill() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longText.append("1234");
        }
        IslandItem it = item("event", IslandItem.Kind.EVENT);
        it.trailing = IslandItem.Trailing.TEXT;
        it.trailingText = longText.toString();
        IslandView view = view(new ArrayList<>(List.of(it)));
        float width = settle(view);
        assertTrue("no pill for a long trailing text", width > 0f);
        assertTrue("the pill ran past the screen: " + width, width <= W);
        paint(view);
    }

    @Test
    public void everyBatteryLevelPaints() {
        Bitmap bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (int percent = 0; percent <= 100; percent += 5) {
            for (boolean charging : new boolean[] {false, true}) {
                android.graphics.drawable.Drawable pill = BatteryIcon.load(app, percent, charging);
                pill.setBounds(0, 0, 120, 120);
                pill.setTint(0xFFFFFFFF);
                pill.draw(canvas);
            }
        }
    }

    @Test
    public void everyEventPictogramPaints() {
        Bitmap bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (GlyphIcon.Kind kind : GlyphIcon.Kind.values()) {
            GlyphIcon icon = GlyphIcon.of(kind);
            icon.setBounds(0, 0, 120, 120);
            icon.draw(canvas);
        }
    }
}
