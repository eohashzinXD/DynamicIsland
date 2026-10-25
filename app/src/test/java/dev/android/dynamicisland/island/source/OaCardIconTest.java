package dev.android.dynamicisland.island.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandHost;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

/** Regression for #8: an app logo on an opaque disc must not turn into a white disc. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class OaCardIconTest {
    private final Application app = RuntimeEnvironment.getApplication();
    private final boolean startedBefore = ReflectionHelpers.getStaticField(ModuleSettings.class, "started");
    private final boolean colorBefore = ReflectionHelpers.getStaticField(ModuleSettings.class, "colorIcons");

    @After
    public void restorePreferences() {
        ReflectionHelpers.setStaticField(ModuleSettings.class, "colorIcons", colorBefore);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "started", startedBefore);
    }

    public static class ActivityData {
        public String mNotiID = "map";
        public String mPackageName = "com.nhn.android.nmap";
        public String mAppName = "NAVER Maps";
        public String mPrimaryInfo = "Turn right";
        public Icon mChipIcon;
        public Icon mCardIcon;
    }

    private IslandItem build(Icon icon, boolean cardFallback) {
        ActivityData data = new ActivityData();
        if (cardFallback) {
            data.mCardIcon = icon;
        } else {
            data.mChipIcon = icon;
        }
        IconLoader icons = new IconLoader(app);
        return new OaCardBuilder(icons, new OaViews(app), new ProgressBinder(icons)).cardFor(new OaActivity(data));
    }

    private Bitmap logo() {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(48, 48, 46, paint);
        paint.setColor(0xFF03C75A);
        canvas.drawRect(32, 32, 64, 64, paint);
        return bitmap;
    }

    private void colorIcons(boolean enabled) {
        // Pin preferences rather than starting the System UI preference-reader thread in a render test.
        ReflectionHelpers.setStaticField(ModuleSettings.class, "started", true);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "colorIcons", enabled);
    }

    @Test
    public void logoKeepsItsDetailInTheDefaultMonochromePill() throws Exception {
        colorIcons(false);
        IslandItem item = build(Icon.createWithBitmap(logo()), false);
        Bitmap out = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        IslandView view = new IslandView(app, IslandHost.NONE);
        view.drawLeadingMono(new Canvas(out), item, 0, 48, 96, 1);
        save("map_pill_mono", out);
        assertTrue("the logo's foreground must remain visible", Color.red(out.getPixel(48, 48)) < 230);
        assertEquals(Color.red(out.getPixel(48, 48)), Color.green(out.getPixel(48, 48)));
        assertEquals(Color.WHITE, out.getPixel(48, 16));
        assertTrue(item.iconIsImage);
    }

    @Test
    public void cardFallbackLogoKeepsItsDetailInTheExpandedCard() throws Exception {
        colorIcons(false);
        IslandItem item = build(Icon.createWithBitmap(logo()), true);
        IslandView view = new IslandView(app, IslandHost.NONE);
        float density = app.getResources().getDisplayMetrics().density;
        float width = 360 * density;
        // The same icon is reused after drawing the grayscale pill; that must not bleach the card.
        view.drawLeadingMono(new Canvas(Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)), item, 0, 48, 96, 1);
        Bitmap out = Bitmap.createBitmap(Math.round(width), Math.round(view.shadeCardHeight(item, width)), Bitmap.Config.ARGB_8888);
        view.drawShadeCard(new Canvas(out), item, width);
        save("map_card", out);
        int pixel = out.getPixel(Math.round(width - 29 * density), Math.round(29 * density));
        assertTrue("the card must preserve the green logo", Color.green(pixel) > Color.red(pixel) + 80);
        assertTrue(item.iconIsImage);
    }

    @Test
    public void colorOptionPreservesLogoColors() throws Exception {
        colorIcons(true);
        IslandItem item = build(Icon.createWithBitmap(logo()), false);
        Bitmap out = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        new IslandView(app, IslandHost.NONE).drawLeadingMono(new Canvas(out), item, 0, 48, 96, 1);
        assertEquals(0xFF03C75A, out.getPixel(48, 48));
    }

    @Test
    public void adaptiveBitmapLogosAreAlsoArtwork() {
        assertTrue(build(Icon.createWithAdaptiveBitmap(logo()), false).iconIsImage);
    }

    @Test
    public void singleColorNavigationGlyphStillGetsAWhiteTint() {
        colorIcons(false);
        Bitmap glyph = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        new Canvas(glyph).drawRect(32, 12, 64, 84, paint);
        IslandItem item = build(Icon.createWithBitmap(glyph), false);
        Bitmap out = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        new IslandView(app, IslandHost.NONE).drawLeadingMono(new Canvas(out), item, 0, 48, 96, 1);
        assertFalse(item.iconIsImage);
        assertEquals(Color.WHITE, out.getPixel(48, 48));
        assertEquals(Color.TRANSPARENT, out.getPixel(16, 48));
    }

    private void save(String name, Bitmap bitmap) throws Exception {
        File dir = new File("build/render/issue8");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
