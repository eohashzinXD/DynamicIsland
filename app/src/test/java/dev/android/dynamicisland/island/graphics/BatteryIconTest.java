package dev.android.dynamicisland.island.graphics;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import dev.android.dynamicisland.island.view.IslandHost;
import dev.android.dynamicisland.island.view.IslandView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class BatteryIconTest {
    @Test public void allLevelsLoadVectorsAndKeepTheirPillAspect() {
        for (int level = 0; level <= 100; level++) {
            Drawable icon = BatteryIcon.load(RuntimeEnvironment.getApplication(), level, true);
            assertNotNull(icon);
            assertFalse("vector missing at " + level, icon instanceof GlyphIcon);
            assertEquals(1.7f, icon.getIntrinsicWidth() / (float) icon.getIntrinsicHeight(), 0.2f);
        }
    }

    @Test public void renderIconSizeComparisonWithoutChangingTheLiveCardSize() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(880, 430, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFF3F3F6);
        canvas.scale(2, 2);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(15);
        canvas.drawText("EVENT icons: current / proposed", 16, 24, paint);
        IslandView owner = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        Drawable battery = BatteryIcon.load(RuntimeEnvironment.getApplication(), 65, true);
        Drawable[] icons = {battery, GlyphIcon.of(GlyphIcon.Kind.BELL), GlyphIcon.of(GlyphIcon.Kind.HEADPHONES)};
        for (int side = 0; side < 2; side++) {
            float left = 12 + side * 218;
            paint.setColor(Colors.CARD_SOLID);
            canvas.drawRoundRect(new RectF(left, 42, left + 206, 198), 22, 22, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(14);
            canvas.drawText(side == 0 ? "22dp (current)" : "40dp (proposed)", left + 16, 68, paint);
            for (int i = 0; i < icons.length; i++) owner.drawDrawable(canvas, icons[i], false,
                    i == 0 ? 0xFF34C759 : Color.WHITE, left + 20 + i * 62, 102, side == 0 ? 22 : 40, 1, 0);
            paint.setColor(0xFF34C759);
            canvas.drawRoundRect(new RectF(left + 16, 166, left + 132, 171), 2.5f, 2.5f, paint);
        }
        File file = new File("build/render/event-icon-size-comparison.png");
        file.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
    }
}
