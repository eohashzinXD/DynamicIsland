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
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

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
        return new MediaBody(new IslandView(app, IslandHost.NONE), item,
                360 * app.getResources().getDisplayMetrics().density);
    }

    private Drawable bitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawColor(Color.BLACK);
        return new BitmapDrawable(app.getResources(), bitmap);
    }

    private Rect renderedIcon(MediaBody body, String name) throws Exception {
        return renderedIcon(body, name, true);
    }

    private Rect renderedIcon(MediaBody body, String name, boolean centeredInk) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap((int) Math.ceil(body.w), (int) Math.ceil(body.h), Bitmap.Config.ARGB_8888);
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
        if (centeredInk) {
            assertEquals(custom.r.centerX(), ink.exactCenterX(), 1);
            assertEquals(custom.r.centerY(), ink.exactCenterY(), 1);
        }
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

    @Test
    public void frameworkFavoriteStarRemainsCenteredAndSmall() throws Exception {
        Drawable star = app.getDrawable(android.R.drawable.btn_star_big_on).getCurrent();
        // Measure the resource at its natural ratio, including its asymmetric built-in padding.
        Bitmap reference = Bitmap.createBitmap(star.getIntrinsicWidth() * 4,
                star.getIntrinsicHeight() * 4, Bitmap.Config.ARGB_8888);
        star.setBounds(0, 0, reference.getWidth(), reference.getHeight());
        star.setTint(Color.WHITE);
        star.draw(new Canvas(reference));
        Rect naturalInk = new Rect(reference.getWidth(), reference.getHeight(), 0, 0);
        for (int y = 0; y < reference.getHeight(); y++) {
            for (int x = 0; x < reference.getWidth(); x++) {
                int pixel = reference.getPixel(x, y);
                if (Color.alpha(pixel) > 200 && Color.red(pixel) > 200) {
                    naturalInk.left = Math.min(naturalInk.left, x);
                    naturalInk.top = Math.min(naturalInk.top, y);
                    naturalInk.right = Math.max(naturalInk.right, x + 1);
                    naturalInk.bottom = Math.max(naturalInk.bottom, y + 1);
                }
            }
        }
        assertTrue(naturalInk.width() > 0 && naturalInk.height() > 0);
        MediaBody body = body(star, () -> { });
        // The star's visible pixels have asymmetric padding; centre its original drawable bounds.
        Rect ink = renderedIcon(body, "favorite_star", false);
        assertEquals(body.buttons.get(0).r.centerX(), star.getBounds().exactCenterX(), 1);
        assertEquals(body.buttons.get(0).r.centerY(), star.getBounds().exactCenterY(), 1);
        assertEquals(naturalInk.width() / (float) naturalInk.height(),
                ink.width() / (float) ink.height(), 0.12);
        assertTrue(ink.width() <= 24 && ink.height() <= 24);
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    public void sizeAndAspectRatioHoldOnAHighDensityScreen() throws Exception {
        float density = app.getResources().getDisplayMetrics().density;
        assertTrue(density > 1);
        Rect ink = renderedIcon(body(bitmap(48, 24), () -> { }), "wide_xxhdpi");
        assertTrue(ink.width() <= 24 * density);
        assertEquals(2, ink.width() / (float) ink.height(), 0.1);
    }

    @Test
    public void pressEffectShrinksTheIconUniformlyWithoutChangingTheTarget() throws Exception {
        MediaBody body = body(bitmap(48, 24), () -> { });
        Btn custom = body.buttons.get(0);
        Rect original = renderedIcon(body, "unpressed");
        android.graphics.RectF target = new android.graphics.RectF(custom.r);
        assertTrue(body.owner.shadeButtonDown(body.item, body.w, target.centerX(), target.centerY()));
        ShadowSystemClock.advanceBy(Duration.ofMillis(110));
        Rect pressed = renderedIcon(body, "pressed");
        assertTrue(pressed.width() < original.width());
        assertEquals(2, pressed.width() / (float) pressed.height(), 0.2);
        assertEquals(target, custom.r);
        body.owner.shadeButtonUp();
    }
}
