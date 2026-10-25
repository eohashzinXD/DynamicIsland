package dev.android.dynamicisland.island.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Paint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Clock text is laid out in fixed digit cells, so the same width is measured at every size and digit. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class DigitClockWidthTest {
    @Test
    public void everyDigitStringOfTheSameShapeHasTheSameWidth() {
        for (float size : new float[] {8f, 14.5f, 30f, 61f}) {
            Paint paint = new Paint();
            paint.setTextSize(size);
            float w = DigitClock.width(paint, "0:00");
            assertEquals(w, DigitClock.width(paint, "1:11"), 0.001f);
            assertEquals(w, DigitClock.width(paint, "8:59"), 0.001f);
            assertTrue(DigitClock.width(paint, "10:00") > w);
        }
    }

    @Test
    public void plainTextIsStillMeasuredAsText() {
        Paint paint = new Paint();
        assertEquals(paint.measureText("2 - 1"), DigitClock.width(paint, "2 - 1"), 0.001f);
    }
}
