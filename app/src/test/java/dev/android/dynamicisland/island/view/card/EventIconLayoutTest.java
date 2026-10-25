package dev.android.dynamicisland.island.view.card;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandHost;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.settings.ModuleSettings;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class EventIconLayoutTest {
    static class Icon extends ColorDrawable {
        Rect painted;
        Icon() { super(Color.GREEN); }
        @Override public void draw(Canvas canvas) { painted = new Rect(getBounds()); super.draw(canvas); }
    }
    @After public void reset() { ReflectionHelpers.setStaticField(ModuleSettings.class, "compactEventIcons", false); }

    @Test public void bothSizesStayInsideNarrowCardsAndLeaveTextItsOwnColumn() {
        for (int width : new int[]{180, 240, 360}) for (boolean small : new boolean[]{false, true}) {
            ReflectionHelpers.setStaticField(ModuleSettings.class, "compactEventIcons", small);
            IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
            view.titlePaint.setTextSize(view.titlePaint.getTextSize() * 1.6f);
            view.bodyPaint.setTextSize(view.bodyPaint.getTextSize() * 1.6f);
            IslandItem item = new IslandItem("event:charge");
            item.kind = IslandItem.Kind.EVENT;
            item.title = "A very long charging-event title";
            item.subtitle = "A detailed subtitle that must not touch the icon";
            item.progress = 0.65f;
            Icon icon = new Icon();
            item.icon = icon;
            LiveBody body = new LiveBody(view, item, width);
            Bitmap bitmap = Bitmap.createBitmap(width + 48, (int) body.h + 48, Bitmap.Config.ARGB_8888);
            body.draw(new Canvas(bitmap), 24, 24, 1, 2000);
            assertEquals(small ? 22 : 40, icon.painted.width());
            assertTrue(icon.painted.right <= 24 + width - body.pad);
            assertTrue(24 + body.pad + body.textW <= icon.painted.left - 10);
            for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
                if (x < 24 || x >= 24 + width || y < 24 || y >= 24 + Math.ceil(body.h)) {
                    assertEquals("content escaped its card at " + width + "dp", 0, Color.alpha(bitmap.getPixel(x, y)));
                }
            }
        }
    }
    @Test public void aHiddenBatteryIconLeavesTheFullTextWidthAndOrdinaryCardsKeepTheirSize() {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        IslandItem item = new IslandItem("event:charging");
        item.kind = IslandItem.Kind.EVENT;
        item.title = "Charging";
        LiveBody hidden = new LiveBody(view, item, 240);
        assertEquals(0, hidden.iconSize, 0);
        assertEquals(240 - hidden.pad * 2, hidden.textW, 1);
        item.kind = IslandItem.Kind.LIVE;
        item.icon = new Icon();
        assertEquals(22, new LiveBody(view, item, 240).iconSize, 0);
    }
}
