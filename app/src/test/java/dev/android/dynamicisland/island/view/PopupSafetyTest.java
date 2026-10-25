package dev.android.dynamicisland.island.view;

import static org.junit.Assert.*;
import dev.android.dynamicisland.island.model.IslandItem;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class PopupSafetyTest {
    @Test public void newPopupCanReplaceAnOpenCardButNeverAnActiveReply() {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(1080, 540, 60, 22, false);
        IslandItem old = new IslandItem("oa:old"), incoming = new IslandItem("peek:new");
        old.title = "Existing card";
        incoming.title = "New message";
        view.setItems(List.of(old));
        assertTrue(view.peek(old, 5000));
        view.setHeld(true);
        assertFalse(view.peek(incoming, 5000));
        assertTrue(view.isShowingCard(old));
        view.setHeld(false);
        assertTrue(view.peek(incoming, 5000));
        assertTrue(view.isShowingCard(incoming));
    }

    @Test public void shadeCopyWaitsForTheDepartingOverlayTextToFade() {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(1080, 540, 60, 22, false);
        IslandItem item = new IslandItem("oa:timer");
        item.title = "A running timer";
        view.setItems(List.of(item));
        long time = android.os.SystemClock.uptimeMillis() * 1_000_000L;
        view.peek(item, 5000);
        for (int i = 0; i < 120; i++) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(16));
            view.doFrame(time += 16_000_000L);
        }
        assertTrue(view.isPaintingCard(item));
        view.setShadeFraction(1);
        assertTrue("full expansion alone must not release a still-painted overlay", view.isPaintingCard(item));
        for (int i = 0; i < 120; i++) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(16));
            view.doFrame(time += 16_000_000L);
        }
        assertFalse(view.isPaintingCard(item));
        assertEquals(0, view.shadeCardCoverageToken());
    }

    @Test public void pulledShadeRestoresItsOwnCardRendering() {
        IslandView view = new IslandView(RuntimeEnvironment.getApplication(), IslandHost.NONE);
        view.setGeometry(1080, 540, 60, 22, false);
        IslandItem item = new IslandItem("oa:one");
        item.title = "Timer";
        view.setItems(List.of(item));
        assertTrue(view.peek(item, 5000));
        assertTrue(view.isShowingCard(item));
        view.setShadeFraction(0.02f);
        assertFalse(view.isShowingCard(item));
        assertFalse(view.canPeek());
    }
}
