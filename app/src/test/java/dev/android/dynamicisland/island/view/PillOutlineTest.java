package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

/** Reproduce the grey pill with NO cards and NO Samsung blur: the filled white outline leaks through. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class PillOutlineTest {
    private void verify(float blob, boolean bubble) throws Exception {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(480, 240, 40, 22, false);
        view.layout(0, 0, 480, 200);
        Scene scene = ReflectionHelpers.getField(view, "scene");
        Springs springs = scene.springs;
        scene.mode = Mode.COMPACT;
        springs.width.snapTo(200);
        springs.height.snapTo(40);
        springs.visibility.snapTo(1);
        springs.outlineFade.snapTo(1);
        springs.pillBlob.snapTo(blob);
        springs.split.snapTo(bubble ? 1 : 0);
        Bitmap bitmap = Bitmap.createBitmap(480, 200, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        RectF pill = new RectF();
        view.computeMain(pill);
        String revision = System.getProperty("outlineCapture", "current");
        File file = new File("build/render/outline-" + revision + "-" + blob + "-" + bubble + ".png");
        file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        int pixel = bitmap.getPixel(Math.round(pill.centerX()), Math.round(pill.centerY()));
        assertEquals("outline must never add white to the fading pill interior (blob=" + blob + ")", 0, Color.red(pixel));
        assertEquals("interior alpha must come only from the black body", Math.round(255 * blob), Color.alpha(pixel), 1);
    }
    @Test public void partiallyReturningPillHasNoGreyFill() throws Exception { verify(0.1f, false); }
    @Test public void halfReturningPillHasNoGreyFill() throws Exception { verify(0.5f, false); }
    @Test public void splitBubbleDoesNotRevealAHiddenPillAsAGreyRectangle() throws Exception { verify(0, true); }
    @Test public void aSettledPillRemainsOpaqueBlack() throws Exception { verify(1, true); }
}
