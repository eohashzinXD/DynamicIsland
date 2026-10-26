package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;
import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=35)
public class AmbientGlowTest {
    private Bitmap draw(AmbientGlow glow,int strength,int colour) {
        Bitmap bitmap=Bitmap.createBitmap(420,160,Bitmap.Config.ARGB_8888);
        glow.draw(new Canvas(bitmap),new RectF(80,55,340,105),colour,1,strength);return bitmap;
    }
    private long alpha(Bitmap bitmap) {
        long sum=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)sum+=Color.alpha(bitmap.getPixel(x,y));return sum;
    }
    @Test public void haloIsOutsideTheBodyAndReusesItsMaskWhenThePillChangesSize() {
        AmbientGlow glow=new AmbientGlow(1);Bitmap bitmap=draw(glow,45,0xFFE0BEA9);
        assertEquals(Color.TRANSPARENT,bitmap.getPixel(210,80));assertTrue(alpha(bitmap)>0);
        Bitmap mask=ReflectionHelpers.getField(glow,"mask");
        glow.draw(new Canvas(bitmap),new RectF(40,50,380,110),Color.BLUE,1,45);
        assertSame(mask,ReflectionHelpers.getField(glow,"mask"));
    }
    @Test public void intensityAndColourOnlyAffectTheHaloAndZeroSkipsAllocation() {
        AmbientGlow glow=new AmbientGlow(1);
        assertEquals(0,alpha(draw(glow,0,Color.RED)));assertNull(ReflectionHelpers.getField(glow,"mask"));
        long low=alpha(draw(glow,25,Color.RED)),normal=alpha(draw(glow,45,Color.RED)),high=alpha(draw(glow,100,Color.RED));
        assertTrue(low<normal);assertTrue(normal<high);
        Bitmap blue=draw(glow,45,Color.BLUE);int pixel=blue.getPixel(210,52);
        assertTrue(Color.alpha(pixel)>0);assertTrue(Color.blue(pixel)>Color.red(pixel));
    }
}
