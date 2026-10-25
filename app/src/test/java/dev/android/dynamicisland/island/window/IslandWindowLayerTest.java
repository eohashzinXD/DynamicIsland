package dev.android.dynamicisland.island.window;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import dev.android.dynamicisland.island.view.IslandHost;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

/** Guards the window-policy choice; real compositor ordering still needs a device test. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class IslandWindowLayerTest {
    @Test
    public void exitAnimationUsesAStatusBarPanelAboveTheNotificationShade() {
        int[] contextType = {0};
        Context systemUi = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Context createWindowContext(Display display, int type, Bundle options) {
                contextType[0] = type;
                return getBaseContext().createDisplayContext(display);
            }
        };
        IslandWindow window = new IslandWindow(systemUi, IslandHost.NONE, new IslandWindow.Listener() {
            @Override public void onWindowConfigurationChanged() { }
            @Override public void onWindowInsetsChanged() { }
        });
        WindowManager.LayoutParams params = ReflectionHelpers.getField(window, "params");
        // Android WindowManagerPolicy places SUB_PANEL (2017) above NOTIFICATION_SHADE (2040).
        // ADDITIONAL (2041) is below it, so a fast opaque shade cuts off the exit animation.
        assertEquals(2017, params.type);
        assertEquals("the window context must accept the same window type", params.type, contextType[0]);
        assertTrue("the overlay must not take focus from the shade", (params.flags
                & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
        assertTrue("outside touches still pass through", (params.flags
                & WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
    }
}
