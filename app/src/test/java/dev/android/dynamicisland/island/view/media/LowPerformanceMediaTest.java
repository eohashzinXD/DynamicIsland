package dev.android.dynamicisland.island.view.media;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.SystemClock;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.island.view.IslandHost;
import dev.android.dynamicisland.settings.ModuleSettings;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=35)
public class LowPerformanceMediaTest {
    @Before public void low() {ModuleSettings.ensureStarted();ReflectionHelpers.setStaticField(ModuleSettings.class,"lowPerformance",true);}
    @After public void restore() {ReflectionHelpers.setStaticField(ModuleSettings.class,"lowPerformance",false);}
    private IslandItem item(int accent) {
        IslandItem item=new IslandItem("music");item.isMedia=true;item.playing=true;item.accent=accent;
        item.art=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);return item;
    }
    private MediaFx effect() {return new MediaFx(new IslandView(RuntimeEnvironment.getApplication(),IslandHost.NONE));}
    @Test public void artworkGhostIsNotAllocatedAndPlayingMusicDoesNotKeepEffectsAwake() {
        MediaFx fx=effect();IslandItem item=item(0xFFAA2020);fx.sync(item,SystemClock.uptimeMillis());
        assertFalse(fx.animating(SystemClock.uptimeMillis()));assertNull(ReflectionHelpers.getField(fx,"blurCur"));
    }
    @Test public void staticAlbumTintRemainsStableButChangesWhenTheAlbumColourChanges() {
        MediaFx fx=effect();long now=SystemClock.uptimeMillis();IslandItem item=item(0xFFCC2020);fx.sync(item,now);
        Bitmap first=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888),second=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        fx.drawGlow(new Canvas(first),0,0,100,100,1,now);fx.advance(now+10000);
        fx.drawGlow(new Canvas(second),0,0,100,100,1,now+10000);assertTrue(first.sameAs(second));
        float[] phases=fx.phase.clone();fx.advance(now+20000);assertArrayEquals(phases,fx.phase,0);
        IslandItem next=item(0xFF2066CC);fx.sync(next,now+20001);assertEquals(0xFF2066CC,fx.accent(now+20001));
        second.eraseColor(Color.TRANSPARENT);fx.drawGlow(new Canvas(second),0,0,100,100,1,now+20001);assertFalse(first.sameAs(second));
    }
    @Test public void playbackPositionUsesActualElapsedTimeEvenWithSparseRendering() {
        MediaFx fx=effect();IslandItem item=item(0xFF2288CC);
        long now=SystemClock.elapsedRealtime();item.mediaPosition=5000;item.mediaPositionUpdateTime=now;item.mediaDuration=120000;
        fx.sync(item,SystemClock.uptimeMillis());
        assertEquals(item.currentMediaPosition(now+3000),fx.shownPosition(item,now+3000));
        item.playing=false;
        assertEquals(item.currentMediaPosition(now+10000),fx.shownPosition(item,now+10000));
    }
}
