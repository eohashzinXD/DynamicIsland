package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import dev.android.dynamicisland.island.graphics.GlyphIcon;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.time.Duration;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class LowPerformanceTest {
    @Before public void prepare() { ModuleSettings.ensureStarted(); low(false); }
    @After public void restore() { low(false); }
    private void low(boolean enabled) { ReflectionHelpers.setStaticField(ModuleSettings.class,"lowPerformance",enabled); }
    private IslandItem music() {
        IslandItem item=new IslandItem("music");item.isMedia=true;item.kind=IslandItem.Kind.MEDIA;
        item.playing=true;item.trailing=IslandItem.Trailing.WAVEFORM;item.title="Music";item.accent=0xFF3E91FF;
        return item;
    }
    private IslandView view(IslandItem item) {
        IslandView view=new IslandView(RuntimeEnvironment.getApplication(),IslandHost.NONE);
        view.setGeometry(600,300,44,22,false);view.layout(0,0,600,1000);view.setItems(List.of(item));return view;
    }
    private void settle(IslandView view) {
        for(int i=0;i<180;i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(16));view.doFrame(SystemClock.uptimeMillis()*1_000_000L);
        }
    }
    @Test public void enablingModeClearsExistingGpuBlurAndDisablingRestoresIt() {
        ContentBlur blur=new ContentBlur();RenderNode window=new RenderNode("window");Canvas target=window.beginRecording(300,200);
        RectF bounds=new RectF(0,0,300,200);
        Canvas content=blur.begin(target,bounds,1,20);assertNotSame(target,content);blur.end(target,content);
        RenderNode node=ReflectionHelpers.getField(blur,"node");assertTrue(node.hasDisplayList());
        low(true);assertSame(target,blur.begin(target,bounds,1,20));assertFalse(node.hasDisplayList());
        low(false);content=blur.begin(target,bounds,1,20);assertNotSame(target,content);blur.end(target,content);window.endRecording();
    }
    @Test public void remoteViewBlurIsClearedWhenModeChanges() {
        class EffectView extends View {
            RenderEffect effect; EffectView(){super(RuntimeEnvironment.getApplication());}
            @Override public void setRenderEffect(RenderEffect value){effect=value;super.setRenderEffect(value);}
        }
        EffectView remote=new EffectView();ContentBlur blur=new ContentBlur();
        blur.apply(remote,1,10);assertNotNull(remote.effect);
        low(true);blur.apply(remote,1,10);assertNull(remote.effect);
        low(false);blur.apply(remote,1,10);assertNotNull(remote.effect);
    }
    @Test public void lowModeStopsTheCompactWaveLoopAndKeepsItsStaticShape() {
        Metrics metrics=new Metrics(1);Waveform wave=new Waveform(metrics);IslandItem item=music();
        assertTrue(wave.step(.016f,item,null));low(true);assertFalse(wave.step(.016f,item,null));
        Bitmap first=Bitmap.createBitmap(60,40,Bitmap.Config.ARGB_8888),second=Bitmap.createBitmap(60,40,Bitmap.Config.ARGB_8888);
        wave.draw(new Canvas(first),new RectF(),10,20,Color.WHITE,1);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5));wave.step(5,item,null);
        wave.draw(new Canvas(second),new RectF(),10,20,Color.WHITE,1);assertTrue(first.sameAs(second));
        low(false);assertTrue(wave.step(.016f,item,null));
    }
    @Test public void settledMusicCardsBecomeIdleAndOpeningRemainsResponsive() {
        low(true);IslandItem item=music();IslandView view=view(item);view.peek(item,60000);
        assertTrue(view.interactiveMotion());settle(view);assertFalse(view.interactiveMotion());
        assertFalse(view.shadeMediaAnimating(item));assertFalse(view.needsAudio());
        // End on an admitted render, not a skipped vsync supplied manually by the test driver.
        ShadowSystemClock.advanceBy(Duration.ofMillis(40));view.doFrame(SystemClock.uptimeMillis()*1_000_000L);
        assertFalse((boolean)ReflectionHelpers.getField(view,"frameScheduled"));
        view.collapse();assertTrue(view.interactiveMotion());
        low(false);assertTrue(view.shadeMediaAnimating(item));
    }
    @Test public void lowModeHasAnOpaqueBlackCardThroughTheEntireFold() {
        low(true);IslandItem item=new IslandItem("live");item.title="Live";IslandView view=view(item);
        StackPainter painter=ReflectionHelpers.getField(view,"stackPainter");
        for(float phase:new float[]{0,.2f,.5f,.8f,1})assertEquals(Color.BLACK,painter.fillColor(phase,phase));
        view.peek(item,60000);settle(view);assertTrue(view.isExpanded());view.collapse();settle(view);assertFalse(view.isExpanded());
    }
    @Test public void iconsAreStillButKeepBatteryStateAndCanResume() {
        GlyphIcon icon=GlyphIcon.battery(.23f,true);boolean normal=icon.animating();low(true);assertFalse(icon.animating());
        assertEquals(.23f,(float)ReflectionHelpers.getField(icon,"level"),0);
        low(false);assertEquals(normal,icon.animating());
    }
    @Test public void hidingTheActualViewStopsWorkAndShowingItRestartsNormalAudioDemand() {
        android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).setup().get();
        IslandView view=view(music());activity.setContentView(view);settle(view);
        assertTrue(view.isShown());assertTrue(view.needsAudio());
        view.setVisibility(View.GONE);
        assertFalse(view.needsAudio());assertFalse((boolean)ReflectionHelpers.getField(view,"frameScheduled"));
        SecondTicker ticker=ReflectionHelpers.getField(view,"ticker");assertFalse((boolean)ReflectionHelpers.getField(ticker,"posted"));
        view.setVisibility(View.VISIBLE);settle(view);assertTrue(view.needsAudio());
        low(true);assertFalse(view.needsAudio());low(false);assertTrue(view.needsAudio());
    }

}
