package dev.android.dynamicisland.island.view.card;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandHost;
import dev.android.dynamicisland.island.view.IslandView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Pixel regressions for #2, through the same button renderer used by the island and shade player. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class MediaCustomIconTest {
    private final Application app = RuntimeEnvironment.getApplication();

    private MediaBody body(Drawable icon, Runnable action) {
        IslandItem item = new IslandItem("media:favorite");
        item.kind = IslandItem.Kind.MEDIA;
        item.isMedia = true;
        item.title = "Song";
        item.mediaCustom.add(new IslandItem.Action("Favorite", icon, null, action, false));
        return new MediaBody(new IslandView(app, IslandHost.NONE), item, 360);
    }

    private Drawable bitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawColor(Color.BLACK);
        return new BitmapDrawable(app.getResources(), bitmap);
    }

    private Rect renderedIcon(MediaBody body, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(360, (int) Math.ceil(body.h), Bitmap.Config.ARGB_8888);
        body.drawButtons(new Canvas(bitmap), 0, 0, 1);
        File dir = new File("build/render/issue2");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        Btn custom = body.buttons.get(0);
        Rect ink = new Rect(bitmap.getWidth(), bitmap.getHeight(), 0, 0);
        for (int y = (int) custom.r.top; y < custom.r.bottom; y++) {
            for (int x = (int) custom.r.left; x < custom.r.right; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) > 200 && Color.red(pixel) > 200) {
                    ink.left = Math.min(ink.left, x);
                    ink.top = Math.min(ink.top, y);
                    ink.right = Math.max(ink.right, x + 1);
                    ink.bottom = Math.max(ink.bottom, y + 1);
                }
            }
        }
        assertTrue("the custom icon was drawn", ink.width() > 0 && ink.height() > 0);
        assertEquals(custom.r.centerX(), ink.exactCenterX(), 1);
        assertEquals(custom.r.centerY(), ink.exactCenterY(), 1);
        return ink;
    }

    @Test
    public void customIconFitsAStandard24dpSlot() throws Exception {
        Rect ink = renderedIcon(body(bitmap(24, 24), () -> { }), "square");
        assertTrue("custom action must not dwarf the transport controls: " + ink, ink.width() <= 24 && ink.height() <= 24);
    }

    @Test
    public void wideBitmapKeepsItsAspectRatio() throws Exception {
        Rect ink = renderedIcon(body(bitmap(48, 24), () -> { }), "wide");
        assertEquals("wide icon must not become square", 2, ink.width() / (float) ink.height(), 0.15);
    }

    @Test
    public void tallNonBitmapDrawableKeepsItsAspectRatio() throws Exception {
        GradientDrawable icon = new GradientDrawable();
        icon.setColor(Color.BLACK);
        icon.setSize(24, 48);
        Rect ink = renderedIcon(body(icon, () -> { }), "tall");
        assertEquals("tall icon must not become square", 0.5, ink.width() / (float) ink.height(), 0.1);
    }

    @Test
    public void iconWithoutIntrinsicDimensionsStillRendersCentered() throws Exception {
        Rect ink = renderedIcon(body(new ColorDrawable(Color.BLACK), () -> { }), "no_intrinsic");
        assertEquals(ink.width(), ink.height());
    }

    @Test
    public void smallerVisualKeepsItsTouchAreaAndAction() {
        AtomicInteger taps = new AtomicInteger();
        MediaBody body = body(bitmap(24, 24), taps::incrementAndGet);
        Btn custom = body.buttons.get(0);
        assertTrue(custom.r.width() >= 42 && custom.r.height() >= 42);
        assertSame(custom, body.hit(custom.r.left + 1, custom.r.centerY()));
        assertSame(custom, body.hit(custom.r.right - 1, custom.r.centerY()));
        body.hit(custom.r.left + 1, custom.r.centerY()).run.run();
        assertEquals(1, taps.get());
    }
}
