package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import dev.android.dynamicisland.island.model.IslandItem;
import java.io.File;
import java.io.FileOutputStream;
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

/** Count coloured content pixels, never the white border, so an empty outlined body cannot pass. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class ContentHandoffFilmTest {
    private void play(int fps, boolean reverse) throws Exception { play(fps, reverse, false); }

    private void play(int fps, boolean reverse, boolean reopen) throws Exception { play(fps, reverse, reopen, false); }

    private void play(int fps, boolean reverse, boolean reopen, boolean custom) throws Exception {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(480, 240, 40, 22, false);
        view.layout(0, 0, 480, 600);
        IslandItem item = new IslandItem("live:handoff");
        item.title = "Live activity";
        item.accent = Color.GREEN;
        item.cardIcon = new ColorDrawable(Color.GREEN);
        item.progress = .5f;
        FrameLayout remote = new FrameLayout(RuntimeEnvironment.getApplication());
        remote.setClipChildren(false);
        view.setRemoteLayer(remote);
        if (custom) {
            item.remoteView = new RemoteViews("android", android.R.layout.simple_list_item_1);
            item.remoteView.setTextViewText(android.R.id.text1, "Custom notification");
            item.remoteView.setInt(android.R.id.text1, "setBackgroundColor", Color.GREEN);
        }
        view.setItems(List.of(item));
        Director director = ReflectionHelpers.getField(view, "director");
        long nanos = SystemClock.uptimeMillis() * 1_000_000L;
        long dt = Math.round(1_000_000_000d / fps);
        for (int i = 0; i < 2 * fps; i++) {
            ShadowSystemClock.advanceBy(Duration.ofNanos(dt)); view.doFrame(nanos += dt);
        }
        Bitmap bitmap = Bitmap.createBitmap(480, 600, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int[] pixels = new int[480 * 600];
        director.expandFromPill();
        for (int stage = 0; stage < (reopen ? 3 : 2); stage++) {
            if (stage == 1) view.collapse();
            if (stage == 2) director.expandFromPill();
            int frames = (stage == 0 && reverse || stage == 1 && reopen) ? Math.round(fps * .08f) : fps;
            for (int i = 0; i < frames; i++) {
                if (stage == 0 && i == (int)Math.ceil(fps * .26)) director.fadePillBlobNow();
                ShadowSystemClock.advanceBy(Duration.ofNanos(dt)); view.doFrame(nanos += dt);
                if (stage == 2) assertEquals("reopening must reuse the moving card", 1, view.cards().size());
                bitmap.eraseColor(Color.BLACK);
                for (int c = view.cards().size() - 1; c >= 0; c--) view.drawCard(canvas, view.cards().get(c), SystemClock.uptimeMillis());
                view.draw(canvas);
                remote.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
                remote.layout(0, 0, 480, 600);
                remote.draw(canvas);
                bitmap.getPixels(pixels, 0, 480, 0, 0, 480, 600);
                int coloured = 0;
                for (int pixel : pixels) {
                    if (Color.green(pixel) > 30 && Color.green(pixel) > Color.red(pixel) + 20
                            && Color.green(pixel) > Color.blue(pixel) + 20 && ++coloured >= 3) break;
                }
                if (coloured < 3) {
                    File file = new File("build/render/empty-handoff-" + fps + "-" + stage + "-" + i + ".png");
                    file.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
                }
                assertTrue("no content at " + fps + "Hz, " + (stage == 0 ? "opening" : "closing") + " frame " + i, coloured >= 3);
            }
        }
    }
    @Test public void contentNeverDisappearsBetweenLayoutsAt60Hz() throws Exception { play(60, false); }
    @Test public void contentNeverDisappearsBetweenLayoutsAt120Hz() throws Exception { play(120, false); }
    @Test public void reversingExpansionAlsoKeepsVisibleContent() throws Exception { play(120, true); }
    @Test public void reopeningDuringCollapseKeepsContentAndDoesNotDuplicateCards() throws Exception { play(120, false, true); }
    @Test public void customNotificationContentAlsoHasNoEmptyHandoff() throws Exception { play(120, false, false, true); }
}
